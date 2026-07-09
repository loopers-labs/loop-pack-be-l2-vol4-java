package com.loopers.domain.productmetrics;

import java.util.Optional;

public interface ProductMetricsRepository {
    Optional<ProductMetricsModel> findByProductId(Long productId);
    ProductMetricsModel save(ProductMetricsModel productMetrics);
}
