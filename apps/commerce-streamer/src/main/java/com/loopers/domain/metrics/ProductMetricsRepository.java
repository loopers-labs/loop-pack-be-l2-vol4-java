package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {
    void applyLike(Long productId, long likeCount, long version);
    void addSales(Long productId, int quantity);
    Optional<ProductMetrics> find(Long productId);
}
