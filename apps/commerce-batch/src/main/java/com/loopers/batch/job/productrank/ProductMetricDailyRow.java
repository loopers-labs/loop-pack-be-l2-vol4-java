package com.loopers.batch.job.productrank;

import java.time.LocalDate;

public record ProductMetricDailyRow(
        String productId,
        LocalDate metricDate,
        long viewCount,
        long likeDeltaCount,
        long purchaseQuantity
) {
}
