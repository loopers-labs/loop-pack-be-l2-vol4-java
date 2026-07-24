package com.loopers.batch.job.ranking;

record AggregatedProductRank(
    Long productId,
    long viewCount,
    long likeCount,
    long saleCount,
    double orderScore,
    double score
) {
}
