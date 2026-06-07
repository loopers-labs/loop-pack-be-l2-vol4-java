package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class PriceTest {

    @DisplayName("Price를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("0이면 입력값을 그대로 보존한 Price가 생성된다.")
        @Test
        void createsPrice_whenValueIsMin() {
            // arrange
            Integer value = 0;

            // act
            Price price = Price.from(value);

            // assert
            assertThat(price.value()).isEqualTo(value);
        }

        @DisplayName("양수면 입력값을 그대로 보존한 Price가 생성된다.")
        @Test
        void createsPrice_whenValueIsPositive() {
            // arrange
            Integer value = 19_900;

            // act
            Price price = Price.from(value);

            // assert
            assertThat(price.value()).isEqualTo(value);
        }

        @DisplayName("0 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            // arrange
            Integer value = -1;

            // act & assert
            assertThatThrownBy(() -> Price.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> Price.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
