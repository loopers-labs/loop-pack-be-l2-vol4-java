package com.loopers.batch.job.ranking;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;

/**
 * 어떤 하루(requestDate)가 속한 ISO 주(週)의 경계·식별자를 나타내는 값 객체(VO).
 * <p>
 * D4 결정: 주 = ISO week. 주 시작은 월요일, 끝은 일요일. 식별자는 {@code WEEK_BASED_YEAR + 주번호}
 * 를 {@code yyyy-'W'ww} 형식으로 표기한다(예: 2026-W30). ISO 는 연말·연초 경계에서
 * "그 주가 어느 해에 속하는가"를 WEEK_BASED_YEAR 로 못박으므로, 문자열 정렬·집계 키로 안전하다.
 * (예: 2026-01-01(목)은 2026-W01 이지만 그 주의 월요일은 2025-12-29 다.)
 */
public class RankingWeek {

    private final String yearWeek;
    private final LocalDate startDate;
    private final LocalDate endDate;

    private RankingWeek(String yearWeek, LocalDate startDate, LocalDate endDate) {
        this.yearWeek = yearWeek;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * requestDate 가 속한 ISO 주의 경계와 식별자를 계산해 RankingWeek 를 만든다.
     * yearWeek 는 {@code WEEK_BASED_YEAR}-W{2자리 주번호}, startDate 는 그 주의 월요일, endDate 는 일요일.
     */
    public static RankingWeek from(LocalDate date) {
        int weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String yearWeek = "%d-W%02d".formatted(weekBasedYear, weekOfYear);

        LocalDate monday = date.with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate sunday = monday.plusDays(6);
        return new RankingWeek(yearWeek, monday, sunday);
    }

    public String yearWeek() {
        return yearWeek;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }
}
