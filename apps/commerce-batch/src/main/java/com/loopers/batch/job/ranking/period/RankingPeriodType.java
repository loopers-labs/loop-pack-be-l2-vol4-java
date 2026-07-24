package com.loopers.batch.job.ranking.period;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 기간 랭킹의 단위(week10). 기간 경계 계산과 대상 MV 테이블을 한곳에 모은다.
 *
 * <p><b>주간은 ISO-8601 기준 월요일 시작</b>이다. 일요일 시작(미국식)과 갈리는 지점이라 한 곳에서만 정하고
 * 나머지는 이 enum 에 위임한다 — 경계 계산이 흩어지면 배치와 API 가 서로 다른 주를 가리키게 된다.
 *
 * <p><b>{@code periodStart} 가 기간의 식별자</b>다. MV 의 PK 선두 컬럼이며, 조회 시에도 요청 날짜를
 * {@link #resolveStart} 로 환산해 찾는다.
 */
public enum RankingPeriodType {

    /** 주간 — 월요일 ~ 일요일. */
    WEEKLY("mv_product_rank_weekly") {
        @Override
        public LocalDate resolveStart(LocalDate date) {
            return date.with(DayOfWeek.MONDAY);
        }

        @Override
        public LocalDate resolveEnd(LocalDate start) {
            return start.plusDays(6);
        }

        @Override
        public LocalDate previousOf(LocalDate date) {
            return resolveStart(date).minusWeeks(1);
        }
    },

    /** 월간 — 1일 ~ 말일. */
    MONTHLY("mv_product_rank_monthly") {
        @Override
        public LocalDate resolveStart(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        public LocalDate resolveEnd(LocalDate start) {
            return start.plusMonths(1).minusDays(1);
        }

        @Override
        public LocalDate previousOf(LocalDate date) {
            return resolveStart(date).minusMonths(1);
        }
    };

    private final String mvTable;

    RankingPeriodType(String mvTable) {
        this.mvTable = mvTable;
    }

    /** 주어진 날짜가 속한 기간의 시작일. */
    public abstract LocalDate resolveStart(LocalDate date);

    /** 기간 시작일로부터의 종료일(포함). */
    public abstract LocalDate resolveEnd(LocalDate start);

    /** 주어진 날짜가 속한 기간의 <b>직전</b> 기간 시작일. 파라미터 없이 돌릴 때의 기본 대상. */
    public abstract LocalDate previousOf(LocalDate date);

    /** 적재 대상 MV 테이블명. */
    public String mvTable() {
        return mvTable;
    }

    /** staging 테이블의 {@code period_type} 값. */
    public String code() {
        return name();
    }
}
