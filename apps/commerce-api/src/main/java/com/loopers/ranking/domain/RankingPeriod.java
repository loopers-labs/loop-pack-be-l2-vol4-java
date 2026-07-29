package com.loopers.ranking.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;

/**
 * 조회 쪽 기간 정책. period_key 형식은 commerce-batch 의 {@code RankingPeriod} 와 같아야 MV 를 찾는다
 * — apps 분리라 계산을 복제한다. 형식이 어긋나면 배치가 저장한 키와 안 맞아 조회가 빈다.
 * DAILY 는 여기 없다 — ZSET 을 2일 창으로 서빙하는 {@link RankingDatePolicy} 가 담당한다.
 */
public enum RankingPeriod {

    WEEKLY("mv_product_rank_weekly") {
        @Override
        public String periodKey(LocalDate date) {
            return "%d-W%02d".formatted(
                    date.get(WeekFields.ISO.weekBasedYear()),
                    date.get(WeekFields.ISO.weekOfWeekBasedYear()));
        }

        @Override
        LocalDate endOf(LocalDate date) {
            return date.with(DayOfWeek.MONDAY).plusDays(6);
        }

        @Override
        LocalDate previous(LocalDate today) {
            return today.minusWeeks(1);
        }
    },

    MONTHLY("mv_product_rank_monthly") {
        @Override
        public String periodKey(LocalDate date) {
            return YearMonth.from(date).toString();
        }

        @Override
        LocalDate endOf(LocalDate date) {
            return YearMonth.from(date).atEndOfMonth();
        }

        @Override
        LocalDate previous(LocalDate today) {
            return today.minusMonths(1);
        }
    };

    private final String tableName;

    RankingPeriod(String tableName) {
        this.tableName = tableName;
    }

    /** MV 테이블명. JDBC 는 식별자를 바인딩 못 하므로 상수로 묶어 외부 입력이 닿지 않게 한다. batch 테이블명과 같다. */
    public String tableName() {
        return tableName;
    }

    public abstract String periodKey(LocalDate date);

    abstract LocalDate endOf(LocalDate date);

    abstract LocalDate previous(LocalDate today);

    /** 그 기간이 오늘 이전에 끝났으면 완결이다. 종료일 당일은 아직 진행 중으로 본다. */
    public boolean isComplete(LocalDate date, LocalDate today) {
        return endOf(date).isBefore(today);
    }

    /** date 를 생략했을 때 줄 가장 최근 확정본의 키 — 지난 주·지난 달. */
    public String latestCompletedKey(LocalDate today) {
        return periodKey(previous(today));
    }
}
