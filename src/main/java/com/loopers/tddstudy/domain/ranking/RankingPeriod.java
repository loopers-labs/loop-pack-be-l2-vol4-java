package com.loopers.tddstudy.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

public record RankingPeriod(String key, LocalDate startDate, LocalDate endDate) {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** baseDate 기준으로 '직전에 끝난 주' (월~일) */
    public static RankingPeriod lastCompletedWeek(LocalDate baseDate) {
        LocalDate thisWeekMonday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = thisWeekMonday.minusWeeks(1);
        LocalDate end = start.plusDays(6);
        return new RankingPeriod(weekKeyOf(start), start, end);
    }

    /** baseDate 기준으로 '직전에 끝난 달' */
    public static RankingPeriod lastCompletedMonth(LocalDate baseDate) {
        LocalDate start = baseDate.withDayOfMonth(1).minusMonths(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return new RankingPeriod(start.format(MONTH_FORMAT), start, end);
    }

    private static String weekKeyOf(LocalDate weekStart) {
        int year = weekStart.get(IsoFields.WEEK_BASED_YEAR);
        int week = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }
}
