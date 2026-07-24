package com.loopers.domain.ranking;

/** 점수까지 계산된 MV 적재 후보 한 줄 — Processor 의 출력. */
public record ProductRankScore(
    Long productId,
    long likeCount,
    long orderCount,
    long viewCount,
    double score
) {}
