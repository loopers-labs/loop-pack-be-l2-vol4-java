package com.loopers.application.ranking;

import java.time.LocalDate;

public record ProductMetricInput(
    LocalDate metricDate,
    Long productId,
    double dailyRankingScore
) {
}
