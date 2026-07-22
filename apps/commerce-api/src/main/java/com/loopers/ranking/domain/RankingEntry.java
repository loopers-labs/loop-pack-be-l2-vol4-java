package com.loopers.ranking.domain;

/**
 * 랭킹 ZSET 조회 결과 한 줄. rank(순위)는 조회 위치로 매기고, score 는 그대로 노출한다.
 */
public record RankingEntry(long productId, double score) {
}
