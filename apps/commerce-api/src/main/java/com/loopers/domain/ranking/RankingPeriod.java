package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static RankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return RankingPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period 는 daily, weekly, monthly 중 하나여야 합니다.");
        }
    }

    public LocalDate startDate(LocalDate targetDate) {
        return switch (this) {
            case DAILY -> targetDate;
            case WEEKLY -> targetDate.with(DayOfWeek.MONDAY);
            case MONTHLY -> targetDate.withDayOfMonth(1);
        };
    }

    public LocalDate endDate(LocalDate targetDate) {
        return switch (this) {
            case DAILY -> targetDate;
            case WEEKLY -> startDate(targetDate).plusDays(6);
            case MONTHLY -> YearMonth.from(targetDate).atEndOfMonth();
        };
    }
}
