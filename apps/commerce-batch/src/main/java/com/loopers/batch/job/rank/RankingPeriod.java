package com.loopers.batch.job.rank;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

/**
 * 랭킹 집계 기간. requestDate 로부터 집계 윈도우(시작~끝)와 MV 저장 키(period_key)를 계산한다. 배치와 API 가 동일 규칙을 써야 조회가 맞는다.
 */
public enum RankingPeriod {

    WEEKLY {
        @Override
        public Window window(LocalDate date) {
            LocalDate monday = date.with(WeekFields.ISO.dayOfWeek(), 1);
            LocalDate sunday = date.with(WeekFields.ISO.dayOfWeek(), 7);
            return new Window(monday, sunday);
        }

        @Override
        public String periodKey(LocalDate date) {
            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            int year = date.get(WeekFields.ISO.weekBasedYear());
            return String.format("%dW%02d", year, week);
        }
    },

    MONTHLY {
        @Override
        public Window window(LocalDate date) {
            LocalDate first = date.withDayOfMonth(1);
            LocalDate last = date.withDayOfMonth(date.lengthOfMonth());
            return new Window(first, last);
        }

        @Override
        public String periodKey(LocalDate date) {
            return date.format(MONTH_KEY);
        }
    };

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    public abstract Window window(LocalDate date);

    public abstract String periodKey(LocalDate date);

    public record Window(LocalDate start, LocalDate end) {}
}