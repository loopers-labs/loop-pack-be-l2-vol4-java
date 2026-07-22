package com.loopers.domain.ranking;

/**
 * 랭킹판의 한 항목(VO). ZSET 이 저장하는 (productId, score) 그대로를 담는다.
 * 상품 상세정보는 여기 없고, 읽기 시 DB(SSOT)에서 hydrate 한다(guide 결정 #7).
 */
public record RankingEntry(long productId, double score) {
}
