package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductLikeCountRepository;
import com.loopers.application.product.ProductLikeCountSnapshot;
import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ProductLikeCountRepositoryImpl implements ProductLikeCountRepository {
    private static final String COUNT_KEY_PREFIX = "product:like-count:";
    private static final String DIRTY_KEY = "product:like-count:dirty";

    private final RedisTemplate<String, String> redisTemplate;

    public ProductLikeCountRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean increase(Long productId, Integer baseLikeCount) {
        return change(productId, baseLikeCount, 1L);
    }

    @Override
    public boolean decrease(Long productId, Integer baseLikeCount) {
        return change(productId, baseLikeCount, -1L);
    }

    @Override
    public Optional<Integer> get(Long productId) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(countKey(productId)))
                .flatMap(this::parseInteger);
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ProductLikeCountSnapshot> getDirtyCounts(int limit) {
        try {
            Set<String> productIds = redisTemplate.opsForSet().members(DIRTY_KEY);
            if (productIds == null || productIds.isEmpty()) {
                return List.of();
            }

            return productIds.stream()
                .flatMap(value -> parseLong(value).stream())
                .sorted(Comparator.naturalOrder())
                .limit(limit)
                .flatMap(productId -> get(productId)
                    .map(likeCount -> new ProductLikeCountSnapshot(productId, likeCount))
                    .stream())
                .toList();
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    @Override
    public void clearDirty(Long productId) {
        runCommand(() -> redisTemplate.opsForSet().remove(DIRTY_KEY, String.valueOf(productId)));
    }

    private boolean change(Long productId, Integer baseLikeCount, long delta) {
        try {
            String key = countKey(productId);
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(safeCount(baseLikeCount)));
            Long changedCount = redisTemplate.opsForValue().increment(key, delta);
            if (changedCount != null && changedCount < 0) {
                redisTemplate.opsForValue().set(key, "0");
            }
            redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(productId));
            return true;
        } catch (DataAccessException | IllegalArgumentException e) {
            return false;
        }
    }

    private String countKey(Long productId) {
        return COUNT_KEY_PREFIX + productId;
    }

    private int safeCount(Integer likeCount) {
        return likeCount == null || likeCount < 0 ? 0 : likeCount;
    }

    private Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Integer> parseInteger(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void runCommand(Runnable command) {
        try {
            command.run();
        } catch (DataAccessException ignored) {
        }
    }
}
