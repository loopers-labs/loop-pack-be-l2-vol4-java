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

class QuantityTest {

    @DisplayName("Quantity 를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("0 이상의 값으로 생성하면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 100, Integer.MAX_VALUE})
        void createsQuantity_whenValueIsNonNegative(int value) {
            // act
            Quantity quantity = Quantity.of(value);

            // assert
            assertThat(quantity.getValue()).isEqualTo(value);
        }

        @DisplayName("음수로 생성하면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {-1, -100, Integer.MIN_VALUE})
        void throwsBadRequest_whenValueIsNegative(int value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> Quantity.of(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("zero() 정적 팩토리는 0을 반환한다.")
        @Test
        void returnsZero_whenZeroIsCalled() {
            // act
            Quantity quantity = Quantity.zero();

            // assert
            assertThat(quantity.getValue()).isZero();
        }
    }

    @DisplayName("Quantity 를 더할 때,")
    @Nested
    class Plus {

        @DisplayName("두 Quantity 의 합을 반환한다.")
        @Test
        void returnsSum() {
            // arrange
            Quantity a = Quantity.of(3);
            Quantity b = Quantity.of(5);

            // act
            Quantity result = a.plus(b);

            // assert
            assertThat(result.getValue()).isEqualTo(8);
        }
    }

    @DisplayName("Quantity 를 뺄 때,")
    @Nested
    class Minus {

        @DisplayName("두 Quantity 의 차를 반환한다.")
        @Test
        void returnsDifference() {
            // arrange
            Quantity a = Quantity.of(10);
            Quantity b = Quantity.of(3);

            // act
            Quantity result = a.minus(b);

            // assert
            assertThat(result.getValue()).isEqualTo(7);
        }

        @DisplayName("결과가 음수가 되는 경우 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenResultIsNegative() {
            // arrange
            Quantity a = Quantity.of(3);
            Quantity b = Quantity.of(5);

            // act
            CoreException result = assertThrows(CoreException.class, () -> a.minus(b));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Quantity 비교는,")
    @Nested
    class Compare {

        @DisplayName("isPositive: value > 0 일 때 true.")
        @Test
        void returnsTrue_whenPositive() {
            assertAll(
                () -> assertThat(Quantity.of(1).isPositive()).isTrue(),
                () -> assertThat(Quantity.of(0).isPositive()).isFalse()
            );
        }

        @DisplayName("isGreaterThanOrEqual: 같거나 크면 true.")
        @Test
        void returnsTrue_whenGreaterOrEqual() {
            assertAll(
                () -> assertThat(Quantity.of(5).isGreaterThanOrEqual(Quantity.of(3))).isTrue(),
                () -> assertThat(Quantity.of(5).isGreaterThanOrEqual(Quantity.of(5))).isTrue(),
                () -> assertThat(Quantity.of(3).isGreaterThanOrEqual(Quantity.of(5))).isFalse()
            );
        }
    }

    @DisplayName("Quantity 동일성은,")
    @Nested
    class Equality {

        @DisplayName("value 가 같으면 같은 객체로 간주된다.")
        @Test
        void isEqual_whenValueIsSame() {
            assertAll(
                () -> assertThat(Quantity.of(5)).isEqualTo(Quantity.of(5)),
                () -> assertThat(Quantity.of(5).hashCode()).isEqualTo(Quantity.of(5).hashCode())
            );
        }
    }
}
