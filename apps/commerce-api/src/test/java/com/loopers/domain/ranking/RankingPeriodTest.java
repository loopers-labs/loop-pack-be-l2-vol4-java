package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingPeriodTest {

    @DisplayName("period 문자열을 파싱할 때,")
    @Nested
    class From {

        @DisplayName("null 또는 빈 값이면 기존 동작(DAILY)으로 취급한다.")
        @Test
        void returnsDaily_whenNullOrBlank() {
            assertAll(
                () -> assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY),
                () -> assertThat(RankingPeriod.from("  ")).isEqualTo(RankingPeriod.DAILY)
            );
        }

        @DisplayName("대소문자·공백에 무관하게 enum 으로 파싱한다.")
        @Test
        void parsesIgnoringCaseAndBlank() {
            assertAll(
                () -> assertThat(RankingPeriod.from("weekly")).isEqualTo(RankingPeriod.WEEKLY),
                () -> assertThat(RankingPeriod.from(" MONTHLY ")).isEqualTo(RankingPeriod.MONTHLY),
                () -> assertThat(RankingPeriod.from("Daily")).isEqualTo(RankingPeriod.DAILY)
            );
        }

        @DisplayName("지원하지 않는 값이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUnsupportedValue() {
            CoreException result = assertThrows(CoreException.class, () -> RankingPeriod.from("yearly"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("집계 대표일로 변환할 때,")
    @Nested
    class ResolveAggregateDate {

        // 2024-01-03 은 수요일, 그 주 월요일은 2024-01-01.
        @DisplayName("WEEKLY 는 date 가 속한 주의 월요일을 반환한다.")
        @Test
        void returnsMondayOfWeek_whenWeekly() {
            assertThat(RankingPeriod.WEEKLY.resolveAggregateDate(LocalDate.of(2024, 1, 3)))
                .isEqualTo(LocalDate.of(2024, 1, 1));
        }

        @DisplayName("MONTHLY 는 date 가 속한 달의 1일을 반환한다.")
        @Test
        void returnsFirstDayOfMonth_whenMonthly() {
            assertThat(RankingPeriod.MONTHLY.resolveAggregateDate(LocalDate.of(2024, 1, 15)))
                .isEqualTo(LocalDate.of(2024, 1, 1));
        }

        @DisplayName("DAILY 는 date 를 그대로 반환한다.")
        @Test
        void returnsSameDate_whenDaily() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            assertThat(RankingPeriod.DAILY.resolveAggregateDate(date)).isEqualTo(date);
        }
    }
}
