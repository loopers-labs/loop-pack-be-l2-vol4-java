package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductCacheStore {

    private static final Duration PRODUCT_TTL = Duration.ofMinutes(5);
    private static final Duration LIST_TTL = Duration.ofSeconds(30);
    private static final String PRODUCT_KEY_PREFIX = "product:detail:";
    private static final String LIST_KEY_PREFIX = "product:list:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<ProductInfo> findProduct(Long productId) {
        String key = productKey(productId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductInfo.class));
        } catch (Exception e) {
            log.warn("상품 상세 캐시 조회 실패. key={}", key, e);
            return Optional.empty();
        }
    }

    public void putProduct(Long productId, ProductInfo info) {
        String key = productKey(productId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(info), PRODUCT_TTL);
        } catch (Exception e) {
            log.warn("상품 상세 캐시 저장 실패. key={}", key, e);
        }
    }

    public Optional<ProductListCacheValue> findList(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductListCacheValue.class));
        } catch (Exception e) {
            log.warn("상품 목록 캐시 조회 실패. key={}", key, e);
            return Optional.empty();
        }
    }

    public void putList(String key, ProductListCacheValue value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), LIST_TTL);
        } catch (Exception e) {
            log.warn("상품 목록 캐시 저장 실패. key={}", key, e);
        }
    }

    public String productKey(Long productId) {
        return PRODUCT_KEY_PREFIX + productId;
    }

    public String listKey(Long brandId, String sort, int page, int size) {
        String brandToken = brandId == null ? "all" : String.valueOf(brandId);
        return LIST_KEY_PREFIX + brandToken + ":" + sort + ":" + page + ":" + size;
    }
}
