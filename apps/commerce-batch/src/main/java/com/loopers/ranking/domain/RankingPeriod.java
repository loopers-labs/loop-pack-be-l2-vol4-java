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
    WEEKLY {
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
    MONTHLY {
        @Override
        public Range resolve(LocalDate targetDate) {
            YearMonth month = YearMonth.from(targetDate);
            return new Range(month.toString(), month.atDay(1), month.atEndOfMonth());
        }
    };

    public abstract Range resolve(LocalDate targetDate);

    public record Range(String periodKey, LocalDate from, LocalDate to) {
    }
}
