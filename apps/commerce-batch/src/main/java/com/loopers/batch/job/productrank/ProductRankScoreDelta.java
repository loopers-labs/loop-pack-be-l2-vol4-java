package com.loopers.batch.job.productrank;

public record ProductRankScoreDelta(
        String productId,
        double scoreDelta,
        long viewDelta,
        long likeDeltaDelta,
        long purchaseDelta
) {
}
