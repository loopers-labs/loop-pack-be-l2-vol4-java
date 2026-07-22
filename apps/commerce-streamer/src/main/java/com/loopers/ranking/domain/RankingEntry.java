package com.loopers.ranking.domain;

/**
 * ZSET 에서 읽은 랭킹 한 줄(내림차순). finalize 스냅샷에 순위를 매겨 저장할 때 쓴다.
 */
public record RankingEntry(long productId, double score) {
}
