package com.loopers.application.ranking;

public record RankedProduct(
    long rank,
    Long productId,
    double score
) {
}
