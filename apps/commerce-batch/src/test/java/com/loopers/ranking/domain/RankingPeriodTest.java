package com.loopers.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * targetDate 가 속한 기간의 키와 날짜 범위를 낸다. 순수 로직(I/O 없음).
 * 주간은 ISO 8601 이라 달력 연도와 주 기준 연도가 어긋나는 구간이 있다 — 양방향 모두 고정한다.
 */
class RankingPeriodTest {

    @Nested
    @DisplayName("주간")
    class Weekly {

        @Test
        @DisplayName("일요일이 주어지면 그 주(월~일) 전체를 범위로 낸다")
        void givenSunday_whenResolved_thenCoversThatMondayToSunday() {
            RankingPeriod.Range range = RankingPeriod.WEEKLY.resolve(LocalDate.of(2026, 7, 26));

            assertThat(range.periodKey()).isEqualTo("2026-W30");
            assertThat(range.from()).isEqualTo(LocalDate.of(2026, 7, 20));
            assertThat(range.to()).isEqualTo(LocalDate.of(2026, 7, 26));
        }

        @Test
        @DisplayName("같은 주의 어느 날을 주든 같은 키와 범위가 나온다")
        void givenAnyDayInWeek_whenResolved_thenSameKeyAndRange() {
            RankingPeriod.Range monday = RankingPeriod.WEEKLY.resolve(LocalDate.of(2026, 7, 20));
            RankingPeriod.Range wednesday = RankingPeriod.WEEKLY.resolve(LocalDate.of(2026, 7, 22));
            RankingPeriod.Range sunday = RankingPeriod.WEEKLY.resolve(LocalDate.of(2026, 7, 26));

            assertThat(monday).isEqualTo(wednesday).isEqualTo(sunday);
        }

        @Test
        @DisplayName("12월 말이지만 ISO 로는 다음 해 1주차다 — getYear() 를 쓰면 2025-W01 이 된다")
        void givenLateDecember_whenResolved_thenBelongsToNextYearWeekOne() {
            RankingPeriod.Range range = RankingPeriod.WEEKLY.resolve(LocalDate.of(2025, 12, 29));

            assertThat(range.periodKey()).isEqualTo("2026-W01");
            assertThat(range.from()).isEqualTo(LocalDate.of(2025, 12, 29));
            assertThat(range.to()).isEqualTo(LocalDate.of(2026, 1, 4));
        }

        @Test
        @DisplayName("1월 초지만 ISO 로는 이전 해 53주차다 — getYear() 를 쓰면 2027-W53 이 된다")
        void givenEarlyJanuary_whenResolved_thenBelongsToPreviousYearLastWeek() {
            RankingPeriod.Range range = RankingPeriod.WEEKLY.resolve(LocalDate.of(2027, 1, 3));

            assertThat(range.periodKey()).isEqualTo("2026-W53");
            assertThat(range.from()).isEqualTo(LocalDate.of(2026, 12, 28));
            assertThat(range.to()).isEqualTo(LocalDate.of(2027, 1, 3));
        }

        @Test
        @DisplayName("두 달에 걸친 주도 하나의 주로 다룬다 — 윤년 2월 말")
        void givenWeekSpanningTwoMonths_whenResolved_thenSingleWeek() {
            RankingPeriod.Range range = RankingPeriod.WEEKLY.resolve(LocalDate.of(2024, 2, 29));

            assertThat(range.periodKey()).isEqualTo("2024-W09");
            assertThat(range.from()).isEqualTo(LocalDate.of(2024, 2, 26));
            assertThat(range.to()).isEqualTo(LocalDate.of(2024, 3, 3));
        }

        @Test
        @DisplayName("주차는 두 자리로 채운다")
        void givenSingleDigitWeek_whenResolved_thenZeroPadded() {
            assertThat(RankingPeriod.WEEKLY.resolve(LocalDate.of(2024, 1, 1)).periodKey())
                    .isEqualTo("2024-W01");
        }
    }

    @Nested
    @DisplayName("월간")
    class Monthly {

        @Test
        @DisplayName("그 달 1일부터 말일까지를 범위로 낸다")
        void givenMidMonth_whenResolved_thenCoversWholeMonth() {
            RankingPeriod.Range range = RankingPeriod.MONTHLY.resolve(LocalDate.of(2026, 7, 22));

            assertThat(range.periodKey()).isEqualTo("2026-07");
            assertThat(range.from()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(range.to()).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @Test
        @DisplayName("윤년 2월 말일은 29일이다")
        void givenLeapFebruary_whenResolved_thenLastDayIs29() {
            RankingPeriod.Range range = RankingPeriod.MONTHLY.resolve(LocalDate.of(2024, 2, 15));

            assertThat(range.periodKey()).isEqualTo("2024-02");
            assertThat(range.to()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("평년 2월 말일은 28일이다")
        void givenNonLeapFebruary_whenResolved_thenLastDayIs28() {
            assertThat(RankingPeriod.MONTHLY.resolve(LocalDate.of(2025, 2, 15)).to())
                    .isEqualTo(LocalDate.of(2025, 2, 28));
        }

        @Test
        @DisplayName("월의 1일과 말일 어느 쪽을 주든 같은 범위가 나온다")
        void givenFirstOrLastDay_whenResolved_thenSameRange() {
            RankingPeriod.Range first = RankingPeriod.MONTHLY.resolve(LocalDate.of(2026, 7, 1));
            RankingPeriod.Range last = RankingPeriod.MONTHLY.resolve(LocalDate.of(2026, 7, 31));

            assertThat(first).isEqualTo(last);
        }

        @Test
        @DisplayName("월은 두 자리로 채운다")
        void givenSingleDigitMonth_whenResolved_thenZeroPadded() {
            assertThat(RankingPeriod.MONTHLY.resolve(LocalDate.of(2026, 3, 10)).periodKey())
                    .isEqualTo("2026-03");
        }
    }
}
