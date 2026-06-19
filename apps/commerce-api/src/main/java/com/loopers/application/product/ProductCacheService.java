package com.loopers.application.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String DETAIL_KEY_PREFIX = "product:detail:";
    private static final String LIST_KEY_PREFIX = "product:list:";
    private static final long DETAIL_TTL_MINUTES = 10;
    private static final long LIST_TTL_MINUTES = 3;

    public Optional<ProductDetailInfo> getProductDetail(Long productId) {
        try {
            String json = redisTemplate.opsForValue().get(DETAIL_KEY_PREFIX + productId);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductDetailInfo.class));
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패 (product:detail:{}): {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    public void cacheProductDetail(Long productId, ProductDetailInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(DETAIL_KEY_PREFIX + productId, json, DETAIL_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패 (product:detail:{}): {}", productId, e.getMessage());
        }
    }

    public Optional<List<ProductDetailInfo>> getProductList(Long brandId, ProductSortType sort) {
        try {
            String key = listKey(brandId, sort);
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패 (product:list): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void cacheProductList(Long brandId, ProductSortType sort, List<ProductDetailInfo> list) {
        try {
            String key = listKey(brandId, sort);
            String json = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(key, json, LIST_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패 (product:list): {}", e.getMessage());
        }
    }

    public void evictProductDetail(Long productId) {
        try {
            redisTemplate.delete(DETAIL_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("Redis 캐시 삭제 실패 (product:detail:{}): {}", productId, e.getMessage());
        }
    }

    public void evictAllProductLists() {
        try {
            Set<String> keys = redisTemplate.keys(LIST_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 삭제 실패 (product:list:*): {}", e.getMessage());
        }
    }

    private String listKey(Long brandId, ProductSortType sort) {
        return LIST_KEY_PREFIX + "brand:" + (brandId != null ? brandId : "all") + ":sort:" + sort.name();
    }
}
