package com.loopers.application.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.ProductSort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class ProductCacheService {

    private static final String DETAIL_KEY_PREFIX = "product:detail:";
    private static final String LIST_KEY_PREFIX = "product:list:";
    private static final long TTL_SECONDS = 300;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
        ObjectMapper objectMapper
    ) {
        this.redisTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ProductCacheItem> getDetail(Long productId) {
        try {
            String json = redisTemplate.opsForValue().get(detailKey(productId));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, ProductCacheItem.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putDetail(Long productId, ProductCacheItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForValue().set(detailKey(productId), json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 장애 시 무시 — DB 직접 조회로 fallback
        }
    }

    public void evictDetail(Long productId) {
        try {
            redisTemplate.delete(detailKey(productId));
        } catch (Exception e) {
            // ignore
        }
    }

    public Optional<List<ProductInfo>> getList(ProductSort sort, int page) {
        try {
            String json = redisTemplate.opsForValue().get(listKey(sort, page));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<ProductInfo>>() {}));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Long> getListTotal(ProductSort sort, int page) {
        try {
            String total = redisTemplate.opsForValue().get(totalKey(sort, page));
            if (total == null) return Optional.empty();
            return Optional.of(Long.parseLong(total));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putList(ProductSort sort, int page, List<ProductInfo> items, long totalElements) {
        try {
            String json = objectMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(listKey(sort, page), json, TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(totalKey(sort, page), String.valueOf(totalElements), TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore
        }
    }

    public void evictAllList() {
        try {
            for (ProductSort sort : ProductSort.values()) {
                for (int page = 0; page < 3; page++) {
                    redisTemplate.delete(listKey(sort, page));
                    redisTemplate.delete(totalKey(sort, page));
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + productId;
    }

    private String listKey(ProductSort sort, int page) {
        return LIST_KEY_PREFIX + "sort=" + sort.name() + ":page=" + page;
    }

    private String totalKey(ProductSort sort, int page) {
        return LIST_KEY_PREFIX + "sort=" + sort.name() + ":page=" + page + ":total";
    }
}
