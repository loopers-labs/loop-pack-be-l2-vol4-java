package com.loopers.application.ranking;

/** 랭킹 한 항목 — ZSET 순번(rank, 1-based)과 aggregation 된 상품정보. */
public record RankingItemInfo(
    long rank,
    Long productId,
    String name,
    Long price,
    long likeCount,
    double score
) {}
