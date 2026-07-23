package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankPeriodTest {

    // 2024-01-01(월) ~ 2024-01-07(일) 이 하나의 주다.
    @DisplayName("주간 기간 계산")
    @Nested
    class Weekly {

        @DisplayName("주 중간 날짜가 주어지면, 해당 주의 월요일~일요일 구간을 반환한다.")
        @Test
        void returnsMondayToSunday_whenMidWeekDateIsProvided() {
            // given
            LocalDate wednesday = LocalDate.of(2024, 1, 3);

            // when
            DateRange range = RankPeriod.WEEKLY.resolveRange(wednesday);

            // then
            assertAll(
                () -> assertThat(range.start()).isEqualTo(LocalDate.of(2024, 1, 1)),
                () -> assertThat(range.end()).isEqualTo(LocalDate.of(2024, 1, 7)),
                () -> assertThat(RankPeriod.WEEKLY.aggregateDate(wednesday)).isEqualTo(LocalDate.of(2024, 1, 1))
            );
        }

        @DisplayName("월요일이 주어지면, 그 날이 곧 기간 시작일이다.")
        @Test
        void returnsSameDayAsStart_whenMondayIsProvided() {
            // given
            LocalDate monday = LocalDate.of(2024, 1, 1);

            // when
            DateRange range = RankPeriod.WEEKLY.resolveRange(monday);

            // then
            assertAll(
                () -> assertThat(range.start()).isEqualTo(LocalDate.of(2024, 1, 1)),
                () -> assertThat(range.end()).isEqualTo(LocalDate.of(2024, 1, 7))
            );
        }

        @DisplayName("일요일이 주어지면, 같은 주의 월요일이 시작일이다.")
        @Test
        void returnsMondayOfSameWeek_whenSundayIsProvided() {
            // given
            LocalDate sunday = LocalDate.of(2024, 1, 7);

            // when
            DateRange range = RankPeriod.WEEKLY.resolveRange(sunday);

            // then
            assertAll(
                () -> assertThat(range.start()).isEqualTo(LocalDate.of(2024, 1, 1)),
                () -> assertThat(range.end()).isEqualTo(LocalDate.of(2024, 1, 7))
            );
        }

        @DisplayName("대상 테이블명은 mv_product_rank_weekly 이다.")
        @Test
        void tableNameIsWeekly() {
            assertThat(RankPeriod.WEEKLY.tableName()).isEqualTo("mv_product_rank_weekly");
        }
    }

    @DisplayName("월간 기간 계산")
    @Nested
    class Monthly {

        @DisplayName("월 중간 날짜가 주어지면, 해당 달의 1일~말일 구간을 반환한다.")
        @Test
        void returnsFirstToLastDay_whenMidMonthDateIsProvided() {
            // given
            LocalDate midMonth = LocalDate.of(2024, 1, 15);

            // when
            DateRange range = RankPeriod.MONTHLY.resolveRange(midMonth);

            // then
            assertAll(
                () -> assertThat(range.start()).isEqualTo(LocalDate.of(2024, 1, 1)),
                () -> assertThat(range.end()).isEqualTo(LocalDate.of(2024, 1, 31)),
                () -> assertThat(RankPeriod.MONTHLY.aggregateDate(midMonth)).isEqualTo(LocalDate.of(2024, 1, 1))
            );
        }

        @DisplayName("윤년 2월의 말일은 29일로 계산된다.")
        @Test
        void returnsLeapDay_whenFebruaryOfLeapYearIsProvided() {
            // given
            LocalDate leapFeb = LocalDate.of(2024, 2, 10);

            // when
            DateRange range = RankPeriod.MONTHLY.resolveRange(leapFeb);

            // then
            assertAll(
                () -> assertThat(range.start()).isEqualTo(LocalDate.of(2024, 2, 1)),
                () -> assertThat(range.end()).isEqualTo(LocalDate.of(2024, 2, 29))
            );
        }

        @DisplayName("대상 테이블명은 mv_product_rank_monthly 이다.")
        @Test
        void tableNameIsMonthly() {
            assertThat(RankPeriod.MONTHLY.tableName()).isEqualTo("mv_product_rank_monthly");
        }
    }

    @DisplayName("period 문자열 파싱")
    @Nested
    class From {

        @DisplayName("대소문자와 공백에 무관하게 enum 으로 파싱한다.")
        @Test
        void parsesIgnoringCaseAndBlank() {
            assertAll(
                () -> assertThat(RankPeriod.from("weekly")).isEqualTo(RankPeriod.WEEKLY),
                () -> assertThat(RankPeriod.from(" MONTHLY ")).isEqualTo(RankPeriod.MONTHLY)
            );
        }

        @DisplayName("지원하지 않는 값이면 예외를 던진다.")
        @Test
        void throwsException_whenUnsupportedValueIsProvided() {
            assertThatThrownBy(() -> RankPeriod.from("daily"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @DisplayName("null 또는 빈 값이면 예외를 던진다.")
        @Test
        void throwsException_whenNullOrBlankIsProvided() {
            assertAll(
                () -> assertThatThrownBy(() -> RankPeriod.from(null)).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> RankPeriod.from("  ")).isInstanceOf(IllegalArgumentException.class)
            );
        }
    }
}
