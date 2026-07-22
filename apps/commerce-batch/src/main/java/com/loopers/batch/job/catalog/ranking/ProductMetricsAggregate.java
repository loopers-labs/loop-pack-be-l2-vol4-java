package com.loopers.batch.job.catalog.ranking;

public record ProductMetricsAggregate(
    Long productId,
    Long viewCount,
    Long likeCount,
    Long salesAmount,
    Double score
) {
}
