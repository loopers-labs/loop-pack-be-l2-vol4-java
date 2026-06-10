package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("Money 생성 시")
    @Nested
    class Create {

        @DisplayName("0 이상의 값을 입력하면 정상 생성된다")
        @Test
        void createsMoney_whenValueIsZeroOrPositive() {
            assertAll(
                () -> assertThat(new Money(0L).value()).isZero(),
                () -> assertThat(new Money(1_000L).value()).isEqualTo(1_000L),
                () -> assertThat(Money.ZERO.value()).isZero()
            );
        }

        @DisplayName("음수를 입력하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            CoreException ex = assertThrows(CoreException.class, () -> new Money(-1L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Money.of(null)이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenOfReceivesNull() {
            CoreException ex = assertThrows(CoreException.class, () -> Money.of(null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 합산 시")
    @Nested
    class Add {

        @DisplayName("두 Money를 더하면 합계 Money가 반환된다")
        @Test
        void returnsSum_whenBothValid() {
            Money result = new Money(3_000L).add(new Money(2_000L));
            assertThat(result.value()).isEqualTo(5_000L);
        }

        @DisplayName("합산 결과가 long 범위를 넘어가면 BAD_REQUEST 예외가 발생한다 (silent overflow 방지)")
        @Test
        void throwsBadRequest_whenSumOverflows() {
            Money huge = new Money(Long.MAX_VALUE);
            CoreException ex = assertThrows(CoreException.class, () -> huge.add(new Money(1L)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 곱셈 시")
    @Nested
    class Multiply {

        @DisplayName("정상 곱은 단가 × 수량 결과를 반환한다")
        @Test
        void returnsProduct_whenValid() {
            Money result = new Money(1_500L).multiply(4);
            assertThat(result.value()).isEqualTo(6_000L);
        }

        @DisplayName("곱셈 결과가 long 범위를 넘어가면 BAD_REQUEST 예외가 발생한다 (silent overflow 방지)")
        @Test
        void throwsBadRequest_whenProductOverflows() {
            Money huge = new Money(Long.MAX_VALUE / 2 + 1);
            CoreException ex = assertThrows(CoreException.class, () -> huge.multiply(3));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
