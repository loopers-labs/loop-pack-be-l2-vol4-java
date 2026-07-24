package com.loopers.domain.ranking;

/**
 * 랭킹 ZSET 의 (member, score) 한 쌍.
 * 조회(API) 단계에서 상품정보와 조인하기 전의 원시 순위 데이터다.
 */
public record RankedProduct(Long productId, double score) {
}
