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

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(
        RedisTemplate<String, String> defaultRedisTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
        ObjectMapper objectMapper
    ) {
        this.readTemplate = defaultRedisTemplate;
        this.writeTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ProductCacheItem> getDetail(Long productId) {
        try {
            String json = readTemplate.opsForValue().get(detailKey(productId));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, ProductCacheItem.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putDetail(Long productId, ProductCacheItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            writeTemplate.opsForValue().set(detailKey(productId), json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 장애 시 무시 — DB 직접 조회로 fallback
        }
    }

    public void evictDetail(Long productId) {
        try {
            writeTemplate.delete(detailKey(productId));
        } catch (Exception e) {
            // ignore
        }
    }

    public Optional<List<ProductInfo>> getList(ProductSort sort, int page) {
        try {
            String json = readTemplate.opsForValue().get(listKey(sort, page));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<ProductInfo>>() {}));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putList(ProductSort sort, int page, List<ProductInfo> items) {
        try {
            String json = objectMapper.writeValueAsString(items);
            writeTemplate.opsForValue().set(listKey(sort, page), json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore
        }
    }

    public void evictAllList() {
        try {
            for (ProductSort sort : ProductSort.values()) {
                for (int page = 0; page < 3; page++) {
                    writeTemplate.delete(listKey(sort, page));
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
}
