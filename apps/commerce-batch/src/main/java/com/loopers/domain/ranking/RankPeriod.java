package com.loopers.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// 주간/월간 랭킹 집계 기간을 정의한다.
// - WEEKLY : baseDate 가 속한 주의 월요일 ~ 일요일 (KST) → MV 컬럼 week_start_date
// - MONTHLY: baseDate 가 속한 달의 1일 ~ 말일 (KST)   → MV 컬럼 month_start_date
// aggregateDate(기간 시작일) 는 MV 의 식별 컬럼 값으로 쓰여, 같은 기간을 재집계하면 같은 행을 덮어쓴다.
public enum RankPeriod {

    WEEKLY {
        @Override
        public DateRange resolveRange(LocalDate baseDate) {
            LocalDate start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new DateRange(start, start.plusDays(6));
        }

        @Override
        public String tableName() {
            return "mv_product_rank_weekly";
        }

        @Override
        public String dateColumnName() {
            return "week_start_date";
        }
    },

    MONTHLY {
        @Override
        public DateRange resolveRange(LocalDate baseDate) {
            LocalDate start = baseDate.with(TemporalAdjusters.firstDayOfMonth());
            return new DateRange(start, baseDate.with(TemporalAdjusters.lastDayOfMonth()));
        }

        @Override
        public String tableName() {
            return "mv_product_rank_monthly";
        }

        @Override
        public String dateColumnName() {
            return "month_start_date";
        }
    };

    public abstract DateRange resolveRange(LocalDate baseDate);

    // MV 대상 테이블명. enum 값으로만 결정되므로 SQL 문자열 조립에 사용해도 인젝션 위험이 없다.
    public abstract String tableName();

    // MV 기간 식별 컬럼명(week_start_date / month_start_date). tableName 과 마찬가지로 enum 값으로만 결정된다.
    public abstract String dateColumnName();

    public LocalDate aggregateDate(LocalDate baseDate) {
        return resolveRange(baseDate).start();
    }

    public static RankPeriod from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("기간(period) 파라미터는 필수입니다.");
        }
        try {
            return RankPeriod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 기간(period)입니다: " + value);
        }
    }
}
