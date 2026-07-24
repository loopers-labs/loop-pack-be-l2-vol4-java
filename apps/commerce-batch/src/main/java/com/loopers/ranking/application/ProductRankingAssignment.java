package com.loopers.ranking.application;

public record ProductRankingAssignment(
    long productId,
    double score,
    int rankNo
) {

    public ProductRankingAssignment {
        if (productId < 1) {
            throw new IllegalArgumentException("productId must be positive");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        if (rankNo < 1 || rankNo > 100) {
            throw new IllegalArgumentException("rankNo must be between 1 and 100");
        }
    }
}
