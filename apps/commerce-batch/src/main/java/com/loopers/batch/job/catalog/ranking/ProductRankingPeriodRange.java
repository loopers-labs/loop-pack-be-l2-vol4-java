package com.loopers.batch.job.catalog.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public record ProductRankingPeriodRange(
    LocalDate startDate,
    LocalDate endDate
) {

    public static ProductRankingPeriodRange of(ProductRankingPeriod period, LocalDate baseDate) {
        if (period == null) {
            throw new IllegalArgumentException("랭킹 기간은 필수입니다.");
        }
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate JobParameter는 필수입니다.");
        }

        return switch (period) {
            case WEEKLY -> new ProductRankingPeriodRange(
                baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                baseDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
            case MONTHLY -> new ProductRankingPeriodRange(
                baseDate.withDayOfMonth(1),
                baseDate.withDayOfMonth(baseDate.lengthOfMonth())
            );
        };
    }
}
