package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsRepository {
    Optional<ProductMetrics> findByMetricDateAndProductId(LocalDate metricDate, Long productId);

    ProductMetrics save(ProductMetrics productMetrics);
}
