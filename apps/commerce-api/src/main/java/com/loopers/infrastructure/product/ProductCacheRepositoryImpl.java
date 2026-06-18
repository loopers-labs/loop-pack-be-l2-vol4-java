package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductCacheRepository;
import com.loopers.application.product.ProductInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductCacheRepositoryImpl implements ProductCacheRepository {

    private static final String KEY_PREFIX = "product:detail:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ProductInfo> find(Long productId) {
        String cached = redisTemplate.opsForValue().get(key(productId));
        if (cached == null) {
            return Optional.empty(); // cache miss
        }
        try {
            return Optional.of(objectMapper.readValue(cached, ProductInfo.class)); // cache hit
        } catch (Exception e) {
            log.warn("상품 캐시 역직렬화 실패, 캐시 무시. productId={}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(Long productId, ProductInfo productInfo) {
        try {
            String json = objectMapper.writeValueAsString(productInfo);
            redisTemplate.opsForValue().set(key(productId), json, TTL);
        } catch (Exception e) {
            log.warn("상품 캐시 저장 실패, 무시. productId={}", productId, e);
        }
    }

    @Override
    public void evict(Long productId) {
        redisTemplate.delete(key(productId));
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
