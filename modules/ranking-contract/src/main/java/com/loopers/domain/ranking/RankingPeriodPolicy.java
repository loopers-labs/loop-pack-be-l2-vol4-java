package com.loopers.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class RankingPeriodPolicy {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public RankingDateRange validate(RankingPeriod period, String startDate, String endDate) {
        if (period == null) {
            throw new IllegalArgumentException("period is required.");
        }

        LocalDate parsedStartDate = parseDate(startDate, "startDate");
        LocalDate parsedEndDate = parseDate(endDate, "endDate");

        if (parsedStartDate.isAfter(parsedEndDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate.");
        }

        validateRange(period, parsedStartDate, parsedEndDate);
        return new RankingDateRange(period, parsedStartDate, parsedEndDate);
    }

    private LocalDate parseDate(String date, String fieldName) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        try {
            return LocalDate.parse(date, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must be yyyyMMdd.", e);
        }
    }

    private void validateRange(RankingPeriod period, LocalDate startDate, LocalDate endDate) {
        switch (period) {
            case DAILY -> validateDaily(startDate, endDate);
            case WEEKLY -> validateWeekly(startDate, endDate);
            case MONTHLY -> validateMonthly(startDate, endDate);
        }
    }

    private void validateDaily(LocalDate startDate, LocalDate endDate) {
        if (!startDate.equals(endDate)) {
            throw new IllegalArgumentException("DAILY ranking requires same startDate and endDate.");
        }
    }

    private void validateWeekly(LocalDate startDate, LocalDate endDate) {
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY
            || endDate.getDayOfWeek() != DayOfWeek.SUNDAY
            || !endDate.equals(startDate.plusDays(6))) {
            throw new IllegalArgumentException("WEEKLY ranking requires Monday to Sunday range.");
        }
    }

    private void validateMonthly(LocalDate startDate, LocalDate endDate) {
        if (startDate.getDayOfMonth() != 1 || !endDate.equals(startDate.withDayOfMonth(startDate.lengthOfMonth()))) {
            throw new IllegalArgumentException("MONTHLY ranking requires first day to last day of month.");
        }
    }
}
