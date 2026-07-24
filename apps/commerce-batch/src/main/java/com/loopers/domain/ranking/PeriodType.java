package com.loopers.domain.ranking;

/**
 * 랭킹 집계 기간 단위. Job 파라미터로 전달받아 어느 MV에 어떤 기간으로 적재할지 결정한다.
 * (일간은 기존 Redis 랭킹이 담당하므로 배치는 주간/월간만 다룬다.)
 */
public enum PeriodType {
    WEEKLY,
    MONTHLY
}
