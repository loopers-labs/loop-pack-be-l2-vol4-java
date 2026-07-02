package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetricsModel> find(Long productId);

    ProductMetricsModel save(ProductMetricsModel metrics);
}