package com.loopers.batch.job.ranking;

public record DailyRankingMetric(
    Long productId,
    long viewCount,
    long likeCount,
    long salesCount
) {
}
