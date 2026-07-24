package com.loopers.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public LocalDate periodStart(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");

        return switch (this) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    public LocalDate periodEnd(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");

        return switch (this) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTHLY -> date.with(TemporalAdjusters.lastDayOfMonth());
        };
    }
}
