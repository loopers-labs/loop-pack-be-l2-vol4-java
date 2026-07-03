package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {
    ProductMetrics save(ProductMetrics metrics);
    Optional<ProductMetrics> findByProductId(Long productId);
}
