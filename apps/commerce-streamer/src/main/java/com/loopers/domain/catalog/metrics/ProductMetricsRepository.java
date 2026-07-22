package com.loopers.domain.catalog.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsRepository {
    ProductMetrics save(ProductMetrics metrics);

    Optional<ProductMetrics> findByMetricDateAndProductId(LocalDate metricDate, Long productId);
}
