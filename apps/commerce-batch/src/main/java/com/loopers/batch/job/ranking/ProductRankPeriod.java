package com.loopers.batch.job.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

enum ProductRankPeriod {
    WEEKLY("mv_product_rank_weekly"),
    MONTHLY("mv_product_rank_monthly");

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final String tableName;

    ProductRankPeriod(String tableName) {
        this.tableName = tableName;
    }

    String tableName() {
        return tableName;
    }

    static ProductRankPeriod from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("period is required. allowed values: weekly, monthly");
        }
        return ProductRankPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    static LocalDate parseTargetDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("targetDate is required. format: yyyyMMdd");
        }
        return LocalDate.parse(value, BASIC_DATE);
    }

    LocalDate startDate(LocalDate targetDate) {
        return switch (this) {
            case WEEKLY -> targetDate.with(DayOfWeek.MONDAY);
            case MONTHLY -> targetDate.withDayOfMonth(1);
        };
    }

    LocalDate endDate(LocalDate targetDate) {
        return switch (this) {
            case WEEKLY -> startDate(targetDate).plusDays(6);
            case MONTHLY -> YearMonth.from(targetDate).atEndOfMonth();
        };
    }
}
