package com.loopers.ranking.application;

public record ProductMetricAggregate(
    long productId,
    long viewCount,
    long likeDelta,
    long orderAmount
) {

    public ProductMetricAggregate {
        if (productId < 1) {
            throw new IllegalArgumentException("productId must be positive");
        }
        if (viewCount < 0) {
            throw new IllegalArgumentException("viewCount must not be negative");
        }
        if (orderAmount < 0) {
            throw new IllegalArgumentException("orderAmount must not be negative");
        }
    }
}
