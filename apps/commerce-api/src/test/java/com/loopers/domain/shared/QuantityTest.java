package com.loopers.domain.shared;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityTest {

    @DisplayName("Quantity 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("값이 1 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsLessThanOne() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> Quantity.of(0));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 1 이상이면, 정상 생성된다.")
        @Test
        void createsQuantity_whenValueIsValid() {
            // act
            Quantity quantity = Quantity.of(3);

            // assert
            assertThat(quantity.value()).isEqualTo(3);
        }
    }
}
