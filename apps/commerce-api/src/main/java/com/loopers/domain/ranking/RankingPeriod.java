package com.loopers.domain.ranking;

/**
 * 랭킹 조회 기간 — DAILY는 Redis ZSET(일자별 키), WEEKLY/MONTHLY는 배치가 적재한 MV에서 조회한다.
 */
public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}
