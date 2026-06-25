package com.loopers.product.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductDetailQuery;
import com.loopers.product.application.ProductDetailViewInvalidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Primary
@Component
public class CachedProductDetailQuery implements ProductDetailQuery, ProductDetailViewInvalidator {

    private static final String CACHE_KEY_PREFIX = "product:detail:v1:";
    private static final String LOCK_KEY_SUFFIX = ":lock";
    private static final String LOCK_VALUE = "1";
    private static final String NOT_FOUND_MARKER = "__NOT_FOUND__";

    private final ProductDetailQueryDsl productDetailQueryDsl;
    private final ObjectMapper objectMapper;
    private final ProductDetailCacheProperties properties;
    private final RedisTemplate<String, String> redisTemplate;

    public CachedProductDetailQuery(
        ProductDetailQueryDsl productDetailQueryDsl,
        ObjectMapper objectMapper,
        ProductDetailCacheProperties properties,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.productDetailQueryDsl = productDetailQueryDsl;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<ProductDetailInfo> findVisibleProduct(Long productId) {
        String cacheKey = cacheKey(productId);
        CacheResult cached = read(cacheKey);
        if (cached.hit()) {
            return cached.toOptional();
        }

        String lockKey = lockKey(productId);
        if (lock(lockKey)) {
            try {
                return loadAndCache(productId, cacheKey);
            } finally {
                unlock(lockKey);
            }
        }

        CacheResult cachedAfterWait = waitAndRead(cacheKey);
        if (cachedAfterWait.hit()) {
            return cachedAfterWait.toOptional();
        }

        return loadAndCache(productId, cacheKey);
    }

    @Override
    public void invalidate(Long productId) {
        invalidateAll(List.of(productId));
    }

    @Override
    public void invalidateAll(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }

        List<String> cacheKeys = productIds.stream()
            .map(this::cacheKey)
            .toList();

        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(cacheKeys);
                }
            });
            return;
        }

        delete(cacheKeys);
    }

    private void delete(Collection<String> cacheKeys) {
        try {
            redisTemplate.delete(cacheKeys);
        } catch (RuntimeException e) {
            log.warn("Failed to evict product detail cache. count={}", cacheKeys.size(), e);
        }
    }

    private Optional<ProductDetailInfo> loadAndCache(Long productId, String cacheKey) {
        Optional<ProductDetailInfo> product = productDetailQueryDsl.findVisibleProduct(productId);
        product.ifPresentOrElse(
            info -> write(cacheKey, info),
            () -> writeNotFound(cacheKey)
        );
        return product;
    }

    private CacheResult read(String cacheKey) {
        try {
            String value = redisTemplate.opsForValue().get(cacheKey);
            if (value == null) {
                return CacheResult.miss();
            }
            if (NOT_FOUND_MARKER.equals(value)) {
                return CacheResult.notFound();
            }
            return CacheResult.found(objectMapper.readValue(value, ProductDetailInfo.class));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Failed to read product detail cache. key={}", cacheKey, e);
            return CacheResult.miss();
        }
    }

    private void write(String cacheKey, ProductDetailInfo product) {
        try {
            redisTemplate.opsForValue().set(
                cacheKey,
                objectMapper.writeValueAsString(product),
                detailTtl()
            );
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Failed to write product detail cache. key={}", cacheKey, e);
        }
    }

    private void writeNotFound(String cacheKey) {
        try {
            redisTemplate.opsForValue().set(
                cacheKey,
                NOT_FOUND_MARKER,
                Duration.ofSeconds(properties.negativeTtlSeconds())
            );
        } catch (RuntimeException e) {
            log.warn("Failed to write product detail negative cache. key={}", cacheKey, e);
        }
    }

    private boolean lock(String lockKey) {
        try {
            Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, LOCK_VALUE, Duration.ofSeconds(properties.lockTtlSeconds()));
            return Boolean.TRUE.equals(locked);
        } catch (RuntimeException e) {
            log.warn("Failed to lock product detail cache. key={}", lockKey, e);
            return false;
        }
    }

    private void unlock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (RuntimeException e) {
            log.warn("Failed to unlock product detail cache. key={}", lockKey, e);
        }
    }

    private CacheResult waitAndRead(String cacheKey) {
        for (int attempt = 0; attempt < properties.lockWaitAttempts(); attempt++) {
            waitForCacheFill();
            CacheResult cached = read(cacheKey);
            if (cached.hit()) {
                return cached;
            }
        }
        return CacheResult.miss();
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

    private Duration detailTtl() {
        long jitter = properties.jitterSeconds() == 0
            ? 0
            : ThreadLocalRandom.current().nextLong(properties.jitterSeconds() + 1);
        return Duration.ofSeconds(properties.ttlSeconds() + jitter);
    }

    private String cacheKey(Long productId) {
        return CACHE_KEY_PREFIX + productId;
    }

    private String lockKey(Long productId) {
        return cacheKey(productId) + LOCK_KEY_SUFFIX;
    }

    private record CacheResult(boolean hit, ProductDetailInfo info) {

        static CacheResult miss() {
            return new CacheResult(false, null);
        }

        static CacheResult notFound() {
            return new CacheResult(true, null);
        }

        static CacheResult found(ProductDetailInfo info) {
            return new CacheResult(true, info);
        }

        Optional<ProductDetailInfo> toOptional() {
            return Optional.ofNullable(info);
        }
    }
}
