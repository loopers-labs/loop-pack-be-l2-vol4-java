package com.loopers.product.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.product.application.ProductListInfo;
import com.loopers.product.application.ProductListQuery;
import com.loopers.product.domain.ProductSort;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Primary
@Component
public class CachedProductListQuery implements ProductListQuery {

    private static final String CACHE_KEY_PREFIX = "product:list:v1:";
    private static final String LOCK_KEY_SUFFIX = ":lock";
    private static final String LOCK_VALUE = "1";
    private static final TypeReference<PageResult<ProductListInfo>> PAGE_RESULT_TYPE = new TypeReference<>() {
    };

    private final ProductListQueryDsl productListQueryDsl;
    private final ObjectMapper objectMapper;
    private final ProductListCacheProperties properties;
    private final RedisTemplate<String, String> redisTemplate;

    public CachedProductListQuery(
        ProductListQueryDsl productListQueryDsl,
        ObjectMapper objectMapper,
        ProductListCacheProperties properties,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.productListQueryDsl = productListQueryDsl;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PageResult<ProductListInfo> findVisibleProducts(PageQuery query, Long brandId, ProductSort sort) {
        if (!isCacheable(query)) {
            return productListQueryDsl.findVisibleProducts(query, brandId, sort);
        }

        String cacheKey = cacheKey(query, brandId, sort);
        PageResult<ProductListInfo> cached = read(cacheKey);
        if (cached != null) {
            return cached;
        }

        String lockKey = lockKey(cacheKey);
        if (lock(lockKey)) {
            try {
                return loadAndCache(query, brandId, sort, cacheKey);
            } finally {
                unlock(lockKey);
            }
        }

        PageResult<ProductListInfo> cachedAfterWait = waitAndRead(cacheKey);
        if (cachedAfterWait != null) {
            return cachedAfterWait;
        }

        return loadAndCache(query, brandId, sort, cacheKey);
    }

    private boolean isCacheable(PageQuery query) {
        long requestedItems = ((long) query.page() * query.size()) + query.size();
        return query.size() <= properties.cacheableMaxSize()
            && requestedItems <= properties.cacheableMaxItems();
    }

    private PageResult<ProductListInfo> loadAndCache(
        PageQuery query,
        Long brandId,
        ProductSort sort,
        String cacheKey
    ) {
        PageResult<ProductListInfo> products = productListQueryDsl.findVisibleProducts(query, brandId, sort);
        if (!products.content().isEmpty()) {
            write(cacheKey, products);
        }
        return products;
    }

    private PageResult<ProductListInfo> read(String cacheKey) {
        try {
            String value = redisTemplate.opsForValue().get(cacheKey);
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, PAGE_RESULT_TYPE);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Failed to read product list cache. key={}", cacheKey, e);
            return null;
        }
    }

    private void write(String cacheKey, PageResult<ProductListInfo> products) {
        try {
            redisTemplate.opsForValue().set(
                cacheKey,
                objectMapper.writeValueAsString(products),
                ttl()
            );
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Failed to write product list cache. key={}", cacheKey, e);
        }
    }

    private boolean lock(String lockKey) {
        try {
            Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, LOCK_VALUE, Duration.ofSeconds(properties.lockTtlSeconds()));
            return Boolean.TRUE.equals(locked);
        } catch (RuntimeException e) {
            log.warn("Failed to lock product list cache. key={}", lockKey, e);
            return false;
        }
    }

    private void unlock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (RuntimeException e) {
            log.warn("Failed to unlock product list cache. key={}", lockKey, e);
        }
    }

    private PageResult<ProductListInfo> waitAndRead(String cacheKey) {
        for (int attempt = 0; attempt < properties.lockWaitAttempts(); attempt++) {
            waitForCacheFill();
            PageResult<ProductListInfo> cached = read(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        return null;
    }

    private void waitForCacheFill() {
        if (properties.lockWaitMillis() == 0) {
            return;
        }
        try {
            Thread.sleep(properties.lockWaitMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration ttl() {
        long jitter = properties.jitterSeconds() == 0
            ? 0
            : ThreadLocalRandom.current().nextLong(properties.jitterSeconds() + 1);
        return Duration.ofSeconds(properties.ttlSeconds() + jitter);
    }

    private String cacheKey(PageQuery query, Long brandId, ProductSort sort) {
        String brandKey = brandId == null ? "all" : String.valueOf(brandId);
        return CACHE_KEY_PREFIX
            + "brand:" + brandKey
            + ":sort:" + sortKey(sort)
            + ":page:" + query.page()
            + ":size:" + query.size();
    }

    private String sortKey(ProductSort sort) {
        return switch (sort) {
            case LATEST -> "latest";
            case PRICE_ASC -> "price_asc";
            case LIKES_DESC -> "likes_desc";
        };
    }

    private String lockKey(String cacheKey) {
        return cacheKey + LOCK_KEY_SUFFIX;
    }
}
