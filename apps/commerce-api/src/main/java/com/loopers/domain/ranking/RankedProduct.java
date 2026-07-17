package com.loopers.domain.ranking;

/** display 보드의 한 항목 — ZSET 순서(점수 내림차순)대로 반환된다. */
public record RankedProduct(long productId, double score) {
}
