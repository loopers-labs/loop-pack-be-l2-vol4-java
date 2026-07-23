package com.loopers.domain.productmetrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsDailyRepository {
    Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);
    ProductMetricsDailyModel save(ProductMetricsDailyModel productMetricsDaily);
}
