package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class MinOrderAmountTest {

    @DisplayName("최소 주문 금액을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("1이면 입력값을 그대로 보존한 최소 주문 금액이 생성된다.")
        @Test
        void createsMinOrderAmount_whenValueIsMin() {
            // arrange
            Integer value = 1;

            // act
            MinOrderAmount minOrderAmount = MinOrderAmount.from(value);

            // assert
            assertThat(minOrderAmount.value()).isEqualTo(value);
        }

        @DisplayName("1보다 크면 입력값을 그대로 보존한 최소 주문 금액이 생성된다.")
        @Test
        void createsMinOrderAmount_whenValueIsAboveMin() {
            // arrange
            Integer value = 10_000;

            // act
            MinOrderAmount minOrderAmount = MinOrderAmount.from(value);

            // assert
            assertThat(minOrderAmount.value()).isEqualTo(value);
        }

        @DisplayName("1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsBelowMin() {
            // arrange
            Integer value = 0;

            // act & assert
            assertThatThrownBy(() -> MinOrderAmount.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> MinOrderAmount.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
