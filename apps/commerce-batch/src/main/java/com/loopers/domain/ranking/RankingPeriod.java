package com.loopers.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * 랭킹 집계 기간 — 기준일(baseDate) 하나로 "어느 구간을 집계할지"와 "어떤 키로 적재할지"를 함께 결정한다.
 * commerce-api 의 com.loopers.domain.ranking.RankingPeriod 와 크로스-앱 계약이다(키 형식을 동일하게 유지할 것).
 *
 * <p>주간 키가 {@code date.getYear()} 가 아닌 ISO 주 기준 연도를 쓰는 이유: 연말/연초의 주는 달력 연도와
 * 어긋난다(2027-01-01 은 2026-W53 에 속함). 달력 연도를 쓰면 한 주가 두 키로 쪼개진다.
 */
public enum RankingPeriod {

    DAILY,
    WEEKLY,
    MONTHLY;

    private static final DateTimeFormatter BASIC_ISO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public static RankingPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("period 는 필수입니다. (DAILY|WEEKLY|MONTHLY)");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 period 입니다: " + raw + " (DAILY|WEEKLY|MONTHLY)");
        }
    }

    /** MV 적재/조회 키. 같은 구간에 속한 어떤 날짜를 넣어도 같은 값이 나온다. */
    public String periodKey(LocalDate date) {
        return switch (this) {
            case DAILY -> BASIC_ISO_DATE.format(date);
            case WEEKLY -> "%d-W%02d".formatted(
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> "%d-%02d".formatted(date.getYear(), date.getMonthValue());
        };
    }

    /** 집계 대상 일자 구간의 시작(포함). */
    public LocalDate startOf(LocalDate date) {
        return switch (this) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    /** 집계 대상 일자 구간의 끝(포함). */
    public LocalDate endOf(LocalDate date) {
        return switch (this) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTHLY -> date.with(TemporalAdjusters.lastDayOfMonth());
        };
    }
}
