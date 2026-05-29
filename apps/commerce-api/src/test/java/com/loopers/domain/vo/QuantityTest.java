package com.loopers.domain.vo;

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

    @DisplayName("Quantity 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0 이상의 수량이면, Quantity 가 정상적으로 생성된다.")
        @Test
        void createsQuantity_whenValueIsNonNegative() {
            // act
            Quantity quantity = Quantity.of(10);

            // assert
            assertThat(quantity.getValue()).isEqualTo(10);
        }

        @DisplayName("수량이 0 이면, Quantity 가 정상적으로 생성된다. (품절/재고 0 표현)")
        @Test
        void createsQuantity_whenValueIsZero() {
            // act
            Quantity quantity = Quantity.of(0);

            // assert
            assertThat(quantity.getValue()).isEqualTo(0);
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {-1, -100, Integer.MIN_VALUE})
        void throwsBadRequest_whenValueIsNegative(int negativeValue) {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> Quantity.of(negativeValue));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("수량을 차감할 때, ")
    @Nested
    class Minus {

        @DisplayName("차감된 새 Quantity 를 반환한다.")
        @Test
        void returnsDifference() {
            // act
            Quantity result = Quantity.of(10).minus(Quantity.of(3));

            // assert
            assertThat(result.getValue()).isEqualTo(7);
        }

        @DisplayName("같은 수량을 차감하면, 0 이 된다.")
        @Test
        void returnsZero_whenSubtractingEqualValue() {
            // act
            Quantity result = Quantity.of(5).minus(Quantity.of(5));

            // assert
            assertThat(result.getValue()).isEqualTo(0);
        }

        @DisplayName("결과는 원본과 다른 인스턴스이며, 원본은 변하지 않는다.")
        @Test
        void isImmutable() {
            // arrange
            Quantity original = Quantity.of(10);

            // act
            Quantity result = original.minus(Quantity.of(3));

            // assert
            assertAll(
                () -> assertThat(result).isNotSameAs(original),
                () -> assertThat(original.getValue()).isEqualTo(10)
            );
        }

        @DisplayName("결과가 음수가 되는 차감이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenResultIsNegative() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> Quantity.of(3).minus(Quantity.of(10)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감할 수량이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOtherIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> Quantity.of(10).minus(null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("수량이 크거나 같은지 비교할 때, ")
    @Nested
    class IsGreaterThanOrEqual {

        @DisplayName("크거나 같은지 여부를 반환한다.")
        @Test
        void comparesValues() {
            // act + assert
            assertAll(
                () -> assertThat(Quantity.of(10).isGreaterThanOrEqual(Quantity.of(10))).isTrue(),
                () -> assertThat(Quantity.of(10).isGreaterThanOrEqual(Quantity.of(5))).isTrue(),
                () -> assertThat(Quantity.of(5).isGreaterThanOrEqual(Quantity.of(10))).isFalse()
            );
        }

        @DisplayName("비교할 수량이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOtherIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> Quantity.of(10).isGreaterThanOrEqual(null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Quantity 의 동치를 판단할 때, ")
    @Nested
    class Equality {

        @DisplayName("수량이 같으면 동치이고 hashCode 도 같다.")
        @Test
        void equalWhenSameValue() {
            // act + assert
            assertAll(
                () -> assertThat(Quantity.of(10)).isEqualTo(Quantity.of(10)),
                () -> assertThat(Quantity.of(10)).hasSameHashCodeAs(Quantity.of(10))
            );
        }

        @DisplayName("수량이 다르거나 null/타입이 다르면 동치가 아니다.")
        @Test
        void notEqualWhenDifferent() {
            // act + assert
            assertAll(
                () -> assertThat(Quantity.of(10)).isNotEqualTo(Quantity.of(9)),
                () -> assertThat(Quantity.of(10).equals(null)).isFalse(),
                () -> assertThat(Quantity.of(10).equals(Money.of(10L))).isFalse()
            );
        }
    }
}
