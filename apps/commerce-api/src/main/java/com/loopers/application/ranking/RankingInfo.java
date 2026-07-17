package com.loopers.application.ranking;

public record RankingInfo(
    long rank,
    Long productId,
    String productName,
    String brandName,
    Long price,
    double score,
    boolean fallback
) {}
