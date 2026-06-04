package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("Money 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0 이상의 amount 이면 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 100L, Long.MAX_VALUE})
        void createsMoney_whenAmountIsNonNegative(long amount) {
            // act
            Money money = Money.of(amount);

            // assert
            assertThat(money.getAmount()).isEqualTo(amount);
        }

        @DisplayName("amount 가 음수면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
        void throwsBadRequest_whenAmountIsNegative(long amount) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> Money.of(amount));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 끼리 add 할 때, ")
    @Nested
    class Add {

        @DisplayName("두 amount 의 합으로 새 Money 가 반환된다.")
        @Test
        void addsTwoMonies() {
            // arrange
            Money a = Money.of(1000L);
            Money b = Money.of(2500L);

            // act
            Money result = a.add(b);

            // assert
            assertThat(result.getAmount()).isEqualTo(3500L);
        }

        @DisplayName("합산 결과가 long 범위를 넘으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOverflow() {
            // arrange
            Money a = Money.of(Long.MAX_VALUE);
            Money b = Money.of(1L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> a.add(b));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 를 multiply 할 때, ")
    @Nested
    class Multiply {

        @DisplayName("0 이상의 정수를 곱하면 새 Money 가 반환된다.")
        @Test
        void multipliesByNonNegativeInt() {
            // arrange
            Money money = Money.of(1000L);

            // act
            Money result = money.multiply(3);

            // assert
            assertThat(result.getAmount()).isEqualTo(3000L);
        }

        @DisplayName("0 을 곱하면 0 인 Money 가 반환된다.")
        @Test
        void multipliesByZero() {
            // arrange
            Money money = Money.of(1000L);

            // act
            Money result = money.multiply(0);

            // assert
            assertThat(result.getAmount()).isZero();
        }

        @DisplayName("곱하는 수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMultiplierIsNegative() {
            // arrange
            Money money = Money.of(1000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> money.multiply(-1));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("곱셈 결과가 long 범위를 넘으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOverflow() {
            // arrange
            Money money = Money.of(Long.MAX_VALUE);

            // act
            CoreException result = assertThrows(CoreException.class, () -> money.multiply(2));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 를 비교할 때, ")
    @Nested
    class IsGreaterThanOrEqual {

        @DisplayName("같은 금액이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenEqual() {
            // arrange
            Money a = Money.of(1000L);
            Money b = Money.of(1000L);

            // assert
            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
        }

        @DisplayName("더 큰 금액이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenGreater() {
            // arrange
            Money a = Money.of(2000L);
            Money b = Money.of(1000L);

            // assert
            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
        }

        @DisplayName("더 작은 금액이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenLess() {
            // arrange
            Money a = Money.of(500L);
            Money b = Money.of(1000L);

            // assert
            assertThat(a.isGreaterThanOrEqual(b)).isFalse();
        }
    }
}
