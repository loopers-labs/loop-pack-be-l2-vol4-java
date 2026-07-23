package com.loopers.domain.ranking;

/**
 * 랭킹 ZSET 한 항목의 원소 — 순위(1-based)/상품 식별자/누적 스코어. 상품 상세(이름·가격 등)는 담지 않는다
 * (랭킹 도메인은 productId 참조까지만 알고, 상세 조합은 Facade 책임).
 *
 * @param rank      1부터 시작하는 순위(ZSET 내림차순 랭크 + 1)
 * @param productId 상품 식별자(ZSET member)
 * @param score     누적 랭킹 스코어(ZSET score)
 */
public record RankedProduct(long rank, Long productId, double score) {
}
