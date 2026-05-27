package com.loopers.domain.quantity;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityTest {
    @DisplayName("수량을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("1 이상의 값이면, 정상적으로 생성된다.")
        @Test
        void createsQuantity_whenValueIsOneOrPositive() {
            // arrange
            int value = 3;

            // act
            Quantity quantity = new Quantity(value);

            // assert
            assertThat(quantity.getValue()).isEqualTo(value);
        }

        @DisplayName("1 미만의 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsLessThanOne() {
            // arrange
            int value = 0;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Quantity(value);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
