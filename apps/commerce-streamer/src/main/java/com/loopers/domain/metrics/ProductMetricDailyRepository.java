package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricDailyRepository {
    Optional<ProductMetricDailyEntity> findByProductIdAndMetricDate(String productId, LocalDate metricDate);
    void incrementViewCount(String productId, LocalDate metricDate);
    void incrementLikeDelta(String productId, LocalDate metricDate);
    void decrementLikeDelta(String productId, LocalDate metricDate);
    void incrementPurchaseQuantity(String productId, LocalDate metricDate, long amount);
}
