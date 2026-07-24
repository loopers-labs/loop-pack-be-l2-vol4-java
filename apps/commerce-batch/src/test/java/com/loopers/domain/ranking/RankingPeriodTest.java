package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingPeriodTest {

    @Nested
    @DisplayName("주간")
    class Weekly {

        @DisplayName("기간 키는 ISO 주 기준 'yyyy-Www' 이다.")
        @Test
        void periodKeyIsIsoWeek() {
            // 2026-07-22(수) 가 속한 ISO 주
            assertThat(RankingPeriod.WEEKLY.periodKey(LocalDate.of(2026, 7, 22))).isEqualTo("2026-W30");
        }

        @DisplayName("연말 경계는 ISO 주 기준 연도를 따른다(2026-12-28(월) → 2026-W53).")
        @Test
        void periodKeyFollowsWeekBasedYear() {
            // 2026-12-28 은 달력상 2026년이지만 ISO 주 기준으로도 2026-W53 (2027-W01 은 2027-01-04 부터)
            assertThat(RankingPeriod.WEEKLY.periodKey(LocalDate.of(2026, 12, 28))).isEqualTo("2026-W53");
            // 2027-01-01(금) 은 달력상 2027년이지만 ISO 주로는 2026-W53 에 속한다
            assertThat(RankingPeriod.WEEKLY.periodKey(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
        }

        @DisplayName("집계 구간은 그 주의 월요일 ~ 일요일이다.")
        @Test
        void rangeIsMondayToSunday() {
            LocalDate wednesday = LocalDate.of(2026, 7, 22);
            assertThat(RankingPeriod.WEEKLY.startOf(wednesday)).isEqualTo(LocalDate.of(2026, 7, 20)); // 월
            assertThat(RankingPeriod.WEEKLY.endOf(wednesday)).isEqualTo(LocalDate.of(2026, 7, 26));   // 일
        }
    }

    @Nested
    @DisplayName("월간")
    class Monthly {

        @DisplayName("기간 키는 'yyyy-MM' 이다.")
        @Test
        void periodKeyIsYearMonth() {
            assertThat(RankingPeriod.MONTHLY.periodKey(LocalDate.of(2026, 7, 22))).isEqualTo("2026-07");
        }

        @DisplayName("집계 구간은 그 달 1일 ~ 말일이다(윤년 포함).")
        @Test
        void rangeIsFirstToLastDayOfMonth() {
            LocalDate feb = LocalDate.of(2028, 2, 10); // 윤년
            assertThat(RankingPeriod.MONTHLY.startOf(feb)).isEqualTo(LocalDate.of(2028, 2, 1));
            assertThat(RankingPeriod.MONTHLY.endOf(feb)).isEqualTo(LocalDate.of(2028, 2, 29));
        }
    }

    @Nested
    @DisplayName("일간")
    class Daily {

        @DisplayName("기간 키는 'yyyyMMdd' 이고 구간은 당일 하루다.")
        @Test
        void periodKeyIsBasicIsoDate() {
            LocalDate day = LocalDate.of(2026, 7, 22);
            assertThat(RankingPeriod.DAILY.periodKey(day)).isEqualTo("20260722");
            assertThat(RankingPeriod.DAILY.startOf(day)).isEqualTo(day);
            assertThat(RankingPeriod.DAILY.endOf(day)).isEqualTo(day);
        }
    }

    @Nested
    @DisplayName("파라미터 파싱")
    class Parsing {

        @DisplayName("대소문자 무관하게 파싱한다.")
        @Test
        void parsesIgnoringCase() {
            assertThat(RankingPeriod.from("weekly")).isEqualTo(RankingPeriod.WEEKLY);
            assertThat(RankingPeriod.from("MONTHLY")).isEqualTo(RankingPeriod.MONTHLY);
        }

        @DisplayName("모르는 값이면 예외를 던진다.")
        @Test
        void rejectsUnknown() {
            assertThatThrownBy(() -> RankingPeriod.from("yearly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
        }

        @DisplayName("null/빈 값이면 예외를 던진다.")
        @Test
        void rejectsBlank() {
            assertThatThrownBy(() -> RankingPeriod.from(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RankingPeriod.from(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
