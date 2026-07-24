package com.loopers.batch.job.ranking;

import java.time.LocalDate;

record ProductRankMvRow(
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    int rankNo,
    Long productId,
    double score,
    long viewCount,
    long likeCount,
    long saleCount,
    double orderScore
) {
}
