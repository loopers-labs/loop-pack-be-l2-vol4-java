package com.loopers.domain.ranking;

/**
 * 랭킹 조회 기간(도메인 어휘). 하나의 API 가 기간에 따라 서로 다른 저장소로 갈린다(D5·D8):
 * DAILY 는 Redis ZSET(실시간), WEEKLY/MONTHLY 는 배치가 적재한 Materialized View.
 */
public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}
