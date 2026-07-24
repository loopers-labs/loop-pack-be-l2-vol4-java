package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingMvRequestValidatorTest {

    @DisplayName("validate()를 실행할 때,")
    @Nested
    class Validate {

        @DisplayName("date만 있고 period/periodKey가 없으면 통과한다.")
        @Test
        void passes_whenOnlyDateGiven() {
            assertThatCode(() -> RankingMvRequestValidator.validate("20260719", null, null))
                .doesNotThrowAnyException();
        }

        @DisplayName("아무것도 없으면 통과한다.")
        @Test
        void passes_whenNothingGiven() {
            assertThatCode(() -> RankingMvRequestValidator.validate(null, null, null))
                .doesNotThrowAnyException();
        }

        @DisplayName("WEEKLY period와 올바른 periodKey면 통과한다.")
        @Test
        void passes_whenWeeklyPeriodKeyIsValid() {
            assertThatCode(() -> RankingMvRequestValidator.validate(null, "WEEKLY", "2026W29"))
                .doesNotThrowAnyException();
        }

        @DisplayName("MONTHLY period와 올바른 periodKey면 통과한다.")
        @Test
        void passes_whenMonthlyPeriodKeyIsValid() {
            assertThatCode(() -> RankingMvRequestValidator.validate(null, "MONTHLY", "202607"))
                .doesNotThrowAnyException();
        }

        @DisplayName("date와 period를 함께 주면 예외를 던진다.")
        @Test
        void throws_whenDateAndPeriodBothGiven() {
            assertThatThrownBy(() -> RankingMvRequestValidator.validate("20260719", "WEEKLY", "2026W29"))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("period만 있고 periodKey가 없으면 예외를 던진다.")
        @Test
        void throws_whenPeriodWithoutPeriodKey() {
            assertThatThrownBy(() -> RankingMvRequestValidator.validate(null, "WEEKLY", null))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("periodKey만 있고 period가 없으면 예외를 던진다.")
        @Test
        void throws_whenPeriodKeyWithoutPeriod() {
            assertThatThrownBy(() -> RankingMvRequestValidator.validate(null, null, "2026W29"))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("WEEKLY periodKey 형식이 yyyyWww가 아니면 예외를 던진다.")
        @Test
        void throws_whenWeeklyPeriodKeyFormatIsInvalid() {
            assertThatThrownBy(() -> RankingMvRequestValidator.validate(null, "WEEKLY", "2026-29"))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("MONTHLY periodKey 형식이 yyyyMM이 아니면 예외를 던진다.")
        @Test
        void throws_whenMonthlyPeriodKeyFormatIsInvalid() {
            assertThatThrownBy(() -> RankingMvRequestValidator.validate(null, "MONTHLY", "2026-07"))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("지원하지 않는 period 값이면 예외를 던진다.")
        @Test
        void throws_whenPeriodIsUnsupported() {
            assertThatThrownBy(() -> RankingMvRequestValidator.validate(null, "DAILY", "2026W29"))
                .isInstanceOf(CoreException.class);
        }
    }
}
