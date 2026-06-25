package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductListCache;
import com.loopers.application.product.ProductListCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductListCacheRepositoryImpl implements ProductListCacheRepository {

    private static final String KEY_PREFIX = "product:list:";
    private static final Duration TTL = Duration.ofMinutes(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ProductListCache> find(Long brandId, String sort, int page, int size) {
        String key = key(brandId, sort, page, size);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return Optional.empty(); // cache miss
        }
        try {
            return Optional.of(objectMapper.readValue(cached, ProductListCache.class)); // cache hit
        } catch (Exception e) {
            log.warn("상품 목록 캐시 역직렬화 실패, 캐시 무시. key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(Long brandId, String sort, int page, int size, ProductListCache cache) {
        String key = key(brandId, sort, page, size);
        try {
            String json = objectMapper.writeValueAsString(cache);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("상품 목록 캐시 저장 실패, 무시. key={}", key, e);
        }
    }

    private String key(Long brandId, String sort, int page, int size) {
        String brand = (brandId == null) ? "all" : brandId.toString();
        return KEY_PREFIX + brand + ":" + sort + ":" + page + ":" + size;
    }
}
