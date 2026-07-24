package com.loopers.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * 집계 주기. baseDate가 속한 <b>달력상</b> 주(ISO 월~일) 또는 달(1일~말일)로 범위·키를 계산한다.
 * period_key는 사람이 읽을 수 있는 안정 키로, 재실행 시 같은 주기는 같은 키를 얻어 멱등 적재된다.
 */
public enum Period {

    WEEKLY {
        @Override
        public LocalDate from(LocalDate baseDate) {
            return baseDate.with(DayOfWeek.MONDAY);
        }

        @Override
        public LocalDate to(LocalDate baseDate) {
            return baseDate.with(DayOfWeek.SUNDAY);
        }

        @Override
        public String key(LocalDate baseDate) {
            // ISO 주(월요일 시작, week-based-year) 기준 — from/to와 동일 정의로 통일한다.
            // 로케일 기반 "ww" 포맷은 주 시작 요일이 달라 월요일이 다른 주로 잡히므로 쓰지 않는다.
            int weekYear = baseDate.get(IsoFields.WEEK_BASED_YEAR);
            int week = baseDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return String.format("%04d-W%02d", weekYear, week);
        }
    },

    MONTHLY {
        private final DateTimeFormatter key = DateTimeFormatter.ofPattern("yyyy-MM", Locale.KOREA);

        @Override
        public LocalDate from(LocalDate baseDate) {
            return baseDate.withDayOfMonth(1);
        }

        @Override
        public LocalDate to(LocalDate baseDate) {
            return baseDate.with(TemporalAdjusters.lastDayOfMonth());
        }

        @Override
        public String key(LocalDate baseDate) {
            return baseDate.format(key);
        }
    };

    /** 집계 시작일(포함). */
    public abstract LocalDate from(LocalDate baseDate);

    /** 집계 종료일(포함). */
    public abstract LocalDate to(LocalDate baseDate);

    /** 이 주기의 사람이 읽을 수 있는 키 (예: 2026-W29 / 2026-07). */
    public abstract String key(LocalDate baseDate);
}
