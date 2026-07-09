package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {
    Optional<ProductMetricsModel> findByProductId(Long productId);
    ProductMetricsModel save(ProductMetricsModel model);
}
