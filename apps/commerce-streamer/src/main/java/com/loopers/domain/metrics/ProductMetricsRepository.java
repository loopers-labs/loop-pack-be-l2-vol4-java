package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetricsModel> find(Long productId, LocalDate metricDate);

    ProductMetricsModel save(ProductMetricsModel metrics);
}