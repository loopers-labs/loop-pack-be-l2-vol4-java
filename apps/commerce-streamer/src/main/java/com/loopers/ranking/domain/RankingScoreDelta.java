package com.loopers.ranking.domain;

import java.time.LocalDate;

/**
 * (날짜, 상품)에 더할 합산 점수. 배치를 fold 한 결과이며 ZSET 에 ZINCRBY 로 반영된다.
 * Redis 키 포맷·TTL 은 infrastructure 가 date 로부터 정한다.
 */
public record RankingScoreDelta(LocalDate date, long productId, double score) {
}
