package com.loopers.domain.catalog.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public record RankingPeriodRange(
    LocalDate startDate,
    LocalDate endDate
) {

    public static RankingPeriodRange of(RankingPeriod period, LocalDate baseDate) {
        if (period == null) {
            throw new IllegalArgumentException("랭킹 기간은 필수입니다.");
        }
        if (baseDate == null) {
            throw new IllegalArgumentException("랭킹 기준일은 필수입니다.");
        }

        return switch (period) {
            case DAILY -> new RankingPeriodRange(baseDate, baseDate);
            case WEEKLY -> new RankingPeriodRange(
                baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                baseDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
            case MONTHLY -> new RankingPeriodRange(
                baseDate.withDayOfMonth(1),
                baseDate.withDayOfMonth(baseDate.lengthOfMonth())
            );
        };
    }
}
