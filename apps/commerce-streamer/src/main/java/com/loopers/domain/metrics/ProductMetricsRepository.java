package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsRepository {
    void increaseLikeCount(Long productId, LocalDate metricDate);

    void decreaseLikeCount(Long productId, LocalDate metricDate);

    void increaseViewCount(Long productId, LocalDate metricDate);

    void increaseSaleCount(Long productId, int quantity, double orderScore, LocalDate metricDate);

    Optional<ProductMetrics> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);
}
