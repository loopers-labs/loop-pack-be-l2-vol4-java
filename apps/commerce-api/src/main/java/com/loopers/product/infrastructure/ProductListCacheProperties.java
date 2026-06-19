package com.loopers.product.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("commerce.product-list-cache")
public record ProductListCacheProperties(
    @DefaultValue("10") long ttlSeconds,
    @DefaultValue("3") long jitterSeconds,
    @DefaultValue("3") long lockTtlSeconds,
    @DefaultValue("80") long lockWaitMillis,
    @DefaultValue("3") int lockWaitAttempts,
    @DefaultValue("50") int cacheableMaxSize,
    @DefaultValue("200") int cacheableMaxItems
) {

    public ProductListCacheProperties {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive.");
        }
        if (jitterSeconds < 0) {
            throw new IllegalArgumentException("jitterSeconds must be zero or positive.");
        }
        if (lockTtlSeconds <= 0) {
            throw new IllegalArgumentException("lockTtlSeconds must be positive.");
        }
        if (lockWaitMillis < 0) {
            throw new IllegalArgumentException("lockWaitMillis must be zero or positive.");
        }
        if (lockWaitAttempts < 0) {
            throw new IllegalArgumentException("lockWaitAttempts must be zero or positive.");
        }
        if (cacheableMaxSize <= 0) {
            throw new IllegalArgumentException("cacheableMaxSize must be positive.");
        }
        if (cacheableMaxItems <= 0) {
            throw new IllegalArgumentException("cacheableMaxItems must be positive.");
        }
    }
}
