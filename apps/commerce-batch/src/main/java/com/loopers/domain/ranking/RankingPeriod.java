package com.loopers.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 랭킹 집계 대상 기간 [start, end] (양 끝 포함). 배치가 받은 기준일(baseDate)을 그 날이 속한
 * 주/월 구간으로 환산한다. 이 구간이 그대로 product_metrics 조회 범위(stat_date BETWEEN start AND end)이자
 * MV의 (period_start, period_end)가 된다.
 */
public record RankingPeriod(LocalDate start, LocalDate end) {

    public static RankingPeriod of(PeriodType type, LocalDate baseDate) {
        return switch (type) {
            case WEEKLY -> weekly(baseDate);
            case MONTHLY -> monthly(baseDate);
        };
    }

    /** 월간: baseDate가 속한 달의 1일 ~ 말일. (예: 2026-07-24 → 2026-07-01 ~ 2026-07-31) */
    private static RankingPeriod monthly(LocalDate baseDate) {
        LocalDate start = baseDate.withDayOfMonth(1);
        LocalDate end = baseDate.with(TemporalAdjusters.lastDayOfMonth());
        return new RankingPeriod(start, end);
    }

    /**
     * 주간: baseDate가 속한 "한 주"의 월요일 ~ 일요일 (ISO 8601, 월요일 시작).
     * (예: 2026-07-24(금) → 2026-07-20(월) ~ 2026-07-26(일))
     */
    private static RankingPeriod weekly(LocalDate baseDate) {
        LocalDate start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        return new RankingPeriod(start, end);
    }
}
