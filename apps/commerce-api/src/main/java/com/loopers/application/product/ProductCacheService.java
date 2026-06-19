package com.loopers.application.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.common.PageCriteria;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class ProductCacheService {
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofSeconds(30);
    private static final String LIST_KEYS_KEY = "product:list:keys";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<ProductInfo> getProduct(Long productId) {
        String key = detailKey(productId);
        return get(key)
            .flatMap(value -> read(value, ProductInfo.class, key));
    }

    public void cacheProduct(ProductInfo productInfo) {
        set(detailKey(productInfo.id()), write(productInfo), DETAIL_TTL);
    }

    public Optional<List<ProductInfo>> getProducts(Long brandId, String sort, Integer page, Integer size) {
        String key = listKey(brandId, sort, page, size);
        return get(key)
            .flatMap(value -> read(value, new TypeReference<>() {}, key));
    }

    public void cacheProducts(Long brandId, String sort, Integer page, Integer size, List<ProductInfo> productInfos) {
        String key = listKey(brandId, sort, page, size);
        set(key, write(productInfos), LIST_TTL);
        runCacheCommand(() -> redisTemplate.opsForSet().add(LIST_KEYS_KEY, key));
    }

    public void evictProduct(Long productId) {
        runCacheCommand(() -> redisTemplate.delete(detailKey(productId)));
    }

    public void evictProductLists() {
        runCacheCommand(() -> {
            Set<String> keys = redisTemplate.opsForSet().members(LIST_KEYS_KEY);
            if (keys == null || keys.isEmpty()) {
                return;
            }
            redisTemplate.delete(keys);
            redisTemplate.delete(LIST_KEYS_KEY);
        });
    }

    private String detailKey(Long productId) {
        return "product:detail:" + productId;
    }

    private String listKey(Long brandId, String sort, Integer page, Integer size) {
        ProductSort productSort = ProductSort.from(sort);
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        String brandKey = brandId == null ? "all" : String.valueOf(brandId);
        return "product:list:brand:" + brandKey
            + ":sort:" + productSort.getValue()
            + ":page:" + pageCriteria.page()
            + ":size:" + pageCriteria.size();
    }

    private Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            return Optional.empty();
        }
    }

    private void set(String key, String value, Duration ttl) {
        runCacheCommand(() -> redisTemplate.opsForValue().set(key, value, ttl));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상품 캐시 직렬화에 실패했습니다.", e);
        }
    }

    private <T> Optional<T> read(String value, Class<T> type, String key) {
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException e) {
            evictKey(key);
            return Optional.empty();
        }
    }

    private <T> Optional<T> read(String value, TypeReference<T> type, String key) {
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException e) {
            evictKey(key);
            return Optional.empty();
        }
    }

    private void evictKey(String key) {
        runCacheCommand(() -> redisTemplate.delete(key));
    }

    private void runCacheCommand(Runnable command) {
        try {
            command.run();
        } catch (RedisConnectionFailureException | RedisSystemException ignored) {
        }
    }
}
