package com.loopers.domain.ranking;

/** 일간 랭킹 ZSET 의 한 항목 — 상품 ID 와 가중 점수. */
public record RankedProductEntry(Long productId, double score) {}
