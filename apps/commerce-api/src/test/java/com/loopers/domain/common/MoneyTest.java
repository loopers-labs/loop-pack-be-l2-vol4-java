package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("Money 를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("0 이상의 값으로 생성하면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 1000L, Long.MAX_VALUE})
        void createsMoney_whenAmountIsNonNegative(long amount) {
            // act
            Money money = Money.of(amount);

            // assert
            assertThat(money.getAmount()).isEqualTo(amount);
        }

        @DisplayName("음수로 생성하면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {-1L, -1000L, Long.MIN_VALUE})
        void throwsBadRequest_whenAmountIsNegative(long amount) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> Money.of(amount));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("zero() 정적 팩토리는 0원을 반환한다.")
        @Test
        void returnsZeroMoney_whenZeroIsCalled() {
            // act
            Money money = Money.zero();

            // assert
            assertThat(money.getAmount()).isZero();
        }
    }

    @DisplayName("Money 를 더할 때,")
    @Nested
    class Plus {

        @DisplayName("두 Money 의 amount 합을 반환한다.")
        @Test
        void returnsSum() {
            // arrange
            Money a = Money.of(1000L);
            Money b = Money.of(500L);

            // act
            Money result = a.plus(b);

            // assert
            assertThat(result.getAmount()).isEqualTo(1500L);
        }

        @DisplayName("원본 객체는 변하지 않는다.")
        @Test
        void doesNotMutateOriginal() {
            // arrange
            Money a = Money.of(1000L);
            Money b = Money.of(500L);

            // act
            a.plus(b);

            // assert
            assertAll(
                () -> assertThat(a.getAmount()).isEqualTo(1000L),
                () -> assertThat(b.getAmount()).isEqualTo(500L)
            );
        }
    }

    @DisplayName("Money 를 뺄 때,")
    @Nested
    class Minus {

        @DisplayName("두 Money 의 amount 차를 반환한다.")
        @Test
        void returnsDifference() {
            // arrange
            Money a = Money.of(1000L);
            Money b = Money.of(300L);

            // act
            Money result = a.minus(b);

            // assert
            assertThat(result.getAmount()).isEqualTo(700L);
        }

        @DisplayName("결과가 음수가 되는 경우 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenResultIsNegative() {
            // arrange
            Money a = Money.of(500L);
            Money b = Money.of(1000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> a.minus(b));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 를 정수배 할 때,")
    @Nested
    class Multiply {

        @DisplayName("amount * factor 결과를 반환한다.")
        @Test
        void returnsProduct() {
            // arrange
            Money money = Money.of(1000L);

            // act
            Money result = money.multiply(3);

            // assert
            assertThat(result.getAmount()).isEqualTo(3000L);
        }

        @DisplayName("0 배 하면 0원이 된다.")
        @Test
        void returnsZero_whenFactorIsZero() {
            // arrange
            Money money = Money.of(1000L);

            // act
            Money result = money.multiply(0);

            // assert
            assertThat(result.getAmount()).isZero();
        }

        @DisplayName("음수 배 하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenFactorIsNegative() {
            // arrange
            Money money = Money.of(1000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> money.multiply(-1));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 를 비교할 때,")
    @Nested
    class Compare {

        @DisplayName("isGreaterThanOrEqual: 같거나 크면 true.")
        @Test
        void returnsTrue_whenGreaterOrEqual() {
            assertAll(
                () -> assertThat(Money.of(1000L).isGreaterThanOrEqual(Money.of(500L))).isTrue(),
                () -> assertThat(Money.of(1000L).isGreaterThanOrEqual(Money.of(1000L))).isTrue(),
                () -> assertThat(Money.of(500L).isGreaterThanOrEqual(Money.of(1000L))).isFalse()
            );
        }

        @DisplayName("isPositive: amount > 0 일 때 true.")
        @Test
        void returnsTrue_whenPositive() {
            assertAll(
                () -> assertThat(Money.of(1L).isPositive()).isTrue(),
                () -> assertThat(Money.of(0L).isPositive()).isFalse()
            );
        }
    }

    @DisplayName("Money 동일성은,")
    @Nested
    class Equality {

        @DisplayName("amount 가 같으면 같은 객체로 간주된다.")
        @Test
        void isEqual_whenAmountIsSame() {
            // arrange
            Money a = Money.of(1000L);
            Money b = Money.of(1000L);

            // assert
            assertAll(
                () -> assertThat(a).isEqualTo(b),
                () -> assertThat(a.hashCode()).isEqualTo(b.hashCode())
            );
        }
    }
}
