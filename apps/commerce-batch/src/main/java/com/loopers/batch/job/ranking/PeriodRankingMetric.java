package com.loopers.batch.job.ranking;

import java.math.BigDecimal;

public record PeriodRankingMetric(
    Long productId,
    long viewCount,
    long likeCount,
    long salesCount,
    BigDecimal score
) {
    public PeriodRankingMetric(Long productId, long viewCount, long likeCount, long salesCount) {
        this(productId, viewCount, likeCount, salesCount, null);
    }

    public PeriodRankingMetric withScore(BigDecimal calculatedScore) {
        return new PeriodRankingMetric(productId, viewCount, likeCount, salesCount, calculatedScore);
    }
}
