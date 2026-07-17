package com.loopers.domain.ranking;

public record RankingEntry(
    Long productId,
    long rank,
    double score
) {}
