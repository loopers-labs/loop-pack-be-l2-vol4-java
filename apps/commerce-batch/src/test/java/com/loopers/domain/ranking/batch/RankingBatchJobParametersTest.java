package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RankingBatchJobParametersTest {

    @DisplayName("of()로 WEEKLY 파라미터를 만들 때,")
    @Nested
    class OfWeekly {

        @DisplayName("periodKey(yyyyWww)로부터 해당 ISO 주의 월요일~일요일을 계산한다.")
        @Test
        void resolvesMondayToSunday() {
            RankingBatchJobParameters parameters = RankingBatchJobParameters.of("WEEKLY", "2026W29");

            assertThat(parameters.periodType()).isEqualTo(RankingBatchPeriodType.WEEKLY);
            assertThat(parameters.startDate()).isEqualTo(LocalDate.of(2026, 7, 13));
            assertThat(parameters.endDate()).isEqualTo(LocalDate.of(2026, 7, 19));
        }

        @DisplayName("연도 경계를 넘는 1주차는 전년도 12월로 시작 월요일이 계산될 수 있다.")
        @Test
        void resolvesWeek1AcrossYearBoundary() {
            RankingBatchJobParameters parameters = RankingBatchJobParameters.of("WEEKLY", "2026W01");

            assertThat(parameters.startDate()).isEqualTo(LocalDate.of(2025, 12, 29));
            assertThat(parameters.endDate()).isEqualTo(LocalDate.of(2026, 1, 4));
        }

        @DisplayName("periodKey 형식이 yyyyWww가 아니면 예외를 던진다.")
        @Test
        void throws_whenFormatIsInvalid() {
            assertThatIllegalArgumentException().isThrownBy(() -> RankingBatchJobParameters.of("WEEKLY", "2026-29"));
        }

        @DisplayName("주차가 53을 초과하면 예외를 던진다.")
        @Test
        void throws_whenWeekIsOutOfRange() {
            assertThatIllegalArgumentException().isThrownBy(() -> RankingBatchJobParameters.of("WEEKLY", "2026W54"));
        }
    }

    @DisplayName("of()로 MONTHLY 파라미터를 만들 때,")
    @Nested
    class OfMonthly {

        @DisplayName("periodKey(yyyyMM)로부터 해당 월의 1일~말일을 계산한다.")
        @Test
        void resolvesFirstDayToLastDay() {
            RankingBatchJobParameters parameters = RankingBatchJobParameters.of("MONTHLY", "202607");

            assertThat(parameters.periodType()).isEqualTo(RankingBatchPeriodType.MONTHLY);
            assertThat(parameters.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(parameters.endDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @DisplayName("2월의 말일은 그 해가 윤년인지에 따라 달라진다.")
        @Test
        void resolvesLastDayOfFebruary_consideringLeapYear() {
            RankingBatchJobParameters parameters = RankingBatchJobParameters.of("MONTHLY", "202602");

            assertThat(parameters.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @DisplayName("월이 01~12 범위를 벗어나면 예외를 던진다.")
        @Test
        void throws_whenMonthIsOutOfRange() {
            assertThatIllegalArgumentException().isThrownBy(() -> RankingBatchJobParameters.of("MONTHLY", "202613"));
        }

        @DisplayName("periodKey 형식이 yyyyMM이 아니면 예외를 던진다.")
        @Test
        void throws_whenFormatIsInvalid() {
            assertThatIllegalArgumentException().isThrownBy(() -> RankingBatchJobParameters.of("MONTHLY", "2026-07"));
        }
    }

    @DisplayName("of()에 잘못된 period가 주어지면,")
    @Nested
    class InvalidPeriod {

        @DisplayName("WEEKLY/MONTHLY가 아니면 예외를 던진다.")
        @Test
        void throws_whenPeriodIsUnknown() {
            assertThatIllegalArgumentException().isThrownBy(() -> RankingBatchJobParameters.of("DAILY", "2026W29"));
        }

        @DisplayName("period가 null이면 예외를 던진다.")
        @Test
        void throws_whenPeriodIsNull() {
            assertThatIllegalArgumentException().isThrownBy(() -> RankingBatchJobParameters.of(null, "2026W29"));
        }
    }
}
