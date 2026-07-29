package com.loopers.ranking.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;

/**
 * targetDate 가 속한 기간의 키와 날짜 범위를 낸다. 순수 로직(I/O 없음).
 * 주간·월간 Job 은 이 둘만 다르고 나머지 구현을 공유한다.
 */
public enum RankingPeriod {

    /**
     * ISO 8601 주(월~일). 키는 달력 연도가 아니라 주 기준 연도를 쓴다 —
     * 2025-12-29 는 2026-W01, 2027-01-03 은 2026-W53 이라 getYear() 로는 양방향 모두 틀린다.
     */
    WEEKLY("mv_product_rank_weekly") {
        @Override
        public Range resolve(LocalDate targetDate) {
            LocalDate monday = targetDate.with(DayOfWeek.MONDAY);
            String key = "%d-W%02d".formatted(
                    targetDate.get(WeekFields.ISO.weekBasedYear()),
                    targetDate.get(WeekFields.ISO.weekOfWeekBasedYear()));
            return new Range(key, monday, monday.plusDays(6));
        }
    },

    /** KST 월(1일~말일). 말일은 윤년을 따라간다. */
    MONTHLY("mv_product_rank_monthly") {
        @Override
        public Range resolve(LocalDate targetDate) {
            YearMonth month = YearMonth.from(targetDate);
            return new Range(month.toString(), month.atDay(1), month.atEndOfMonth());
        }
    };

    /**
     * MV 에 담는 개수. 과제 요구는 상위 100이지만 배치 이후 상품이 빠질 수 있어 여유분 50을 더 둔다.
     */
    public static final int RANK_LIMIT = 150;

    private final String tableName;

    RankingPeriod(String tableName) {
        this.tableName = tableName;
    }

    /**
     * MV 테이블명. JDBC 플레이스홀더는 식별자를 바인딩하지 못하므로 SQL 에 문자열로 끼워야 하는데,
     * 여기 상수로 묶어 두면 외부 입력이 테이블명 자리에 닿을 길이 없다.
     */
    public String tableName() {
        return tableName;
    }

    public abstract Range resolve(LocalDate targetDate);

    public record Range(String periodKey, LocalDate from, LocalDate to) {
    }
}
