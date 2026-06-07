package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class QuantityTest {

    @DisplayName("Quantity를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("1이면 입력값을 그대로 보존한 Quantity가 생성된다.")
        @Test
        void createsQuantity_whenValueIsMin() {
            // arrange
            Integer value = 1;

            // act
            Quantity quantity = Quantity.from(value);

            // assert
            assertThat(quantity.value()).isEqualTo(value);
        }

        @DisplayName("1보다 크면 입력값을 그대로 보존한 Quantity가 생성된다.")
        @Test
        void createsQuantity_whenValueIsGreaterThanMin() {
            // arrange
            Integer value = 10;

            // act
            Quantity quantity = Quantity.from(value);

            // assert
            assertThat(quantity.value()).isEqualTo(value);
        }

        @DisplayName("1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void throwsBadRequest_whenValueIsLessThanMin(int value) {
            // arrange & act & assert
            assertThatThrownBy(() -> Quantity.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> Quantity.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
