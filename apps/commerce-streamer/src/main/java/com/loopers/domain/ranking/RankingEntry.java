package com.loopers.domain.ranking;

/**
 * 랭킹판의 한 항목 — Carry-Over가 상위 항목을 읽어 다음 날 키로 이월할 때 사용한다.
 */
public record RankingEntry(Long productId, double score) {
}
