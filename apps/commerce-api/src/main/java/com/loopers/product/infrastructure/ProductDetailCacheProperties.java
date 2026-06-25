package com.loopers.product.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("commerce.product-detail-cache")
public record ProductDetailCacheProperties(
    @DefaultValue("30") long ttlSeconds,
    @DefaultValue("5") long jitterSeconds,
    @DefaultValue("10") long negativeTtlSeconds,
    @DefaultValue("3") long lockTtlSeconds,
    @DefaultValue("80") long lockWaitMillis,
    @DefaultValue("3") int lockWaitAttempts
) {

    public ProductDetailCacheProperties {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive.");
        }
        if (jitterSeconds < 0) {
            throw new IllegalArgumentException("jitterSeconds must be zero or positive.");
        }
        if (negativeTtlSeconds <= 0) {
            throw new IllegalArgumentException("negativeTtlSeconds must be positive.");
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
    }
}
