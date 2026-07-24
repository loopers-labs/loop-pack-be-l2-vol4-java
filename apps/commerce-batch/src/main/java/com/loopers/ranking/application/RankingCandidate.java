package com.loopers.ranking.application;

public record RankingCandidate(
    long snapshotId,
    long productId,
    double score
) {

    public RankingCandidate {
        if (snapshotId < 1) {
            throw new IllegalArgumentException("snapshotId must be positive");
        }
        if (productId < 1) {
            throw new IllegalArgumentException("productId must be positive");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }
}
