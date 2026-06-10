package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class DiscountTypeTest {

    @DisplayName("할인 값을 타입별로 검증할 때,")
    @Nested
    class Validate {

        @DisplayName("할인 값이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> DiscountType.FIXED.validate(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정액 할인 값이 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenFixedValueIsBelowMin() {
            // arrange & act & assert
            assertThatThrownBy(() -> DiscountType.FIXED.validate(0))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정액 할인 값은 1 이상이면 상한 없이 통과한다.")
        @ParameterizedTest
        @ValueSource(ints = {1, 1_000_000})
        void passes_whenFixedValueIsAtLeastMin(int value) {
            // arrange & act & assert
            assertThatCode(() -> DiscountType.FIXED.validate(value)).doesNotThrowAnyException();
        }

        @DisplayName("정률 할인 값이 1~100 범위를 벗어나면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 101})
        void throwsBadRequest_whenRateValueIsOutOfRange(int value) {
            // arrange & act & assert
            assertThatThrownBy(() -> DiscountType.RATE.validate(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 할인 값이 1~100 범위면 통과한다.")
        @ParameterizedTest
        @ValueSource(ints = {1, 100})
        void passes_whenRateValueIsWithinRange(int value) {
            // arrange & act & assert
            assertThatCode(() -> DiscountType.RATE.validate(value)).doesNotThrowAnyException();
        }
    }

    @DisplayName("할인 금액을 타입별로 계산할 때,")
    @Nested
    class Calculate {

        @DisplayName("정액은 할인 값이 주문 금액 이하면 할인 값 그대로 계산된다.")
        @Test
        void returnsFixedValue_whenValueIsWithinOrderAmount() {
            // arrange & act & assert
            assertThat(DiscountType.FIXED.calculate(30_000, 5_000)).isEqualTo(5_000);
        }

        @DisplayName("정액은 할인 값이 주문 금액보다 크면 주문 금액으로 캡된다.")
        @Test
        void capsAtOrderAmount_whenFixedValueExceedsOrderAmount() {
            // arrange & act & assert
            assertThat(DiscountType.FIXED.calculate(30_000, 50_000)).isEqualTo(30_000);
        }

        @DisplayName("정률은 주문 금액의 비율로 계산되며 소수점 이하는 내림한다.")
        @Test
        void floorsRateDiscount() {
            // arrange & act & assert (10,001원의 10% = 1,000.1원 → 1,000원)
            assertThat(DiscountType.RATE.calculate(10_001, 10)).isEqualTo(1_000);
        }

        @DisplayName("정률 100%면 주문 금액 전액이 할인된다.")
        @Test
        void returnsFullAmount_whenRateIsHundred() {
            // arrange & act & assert
            assertThat(DiscountType.RATE.calculate(30_000, 100)).isEqualTo(30_000);
        }
    }
}
