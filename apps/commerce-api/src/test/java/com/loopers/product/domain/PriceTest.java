package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceTest {

    @DisplayName("Price를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("양수 가격이면, 정상 생성된다.")
        @Test
        void createsPrice_whenValueIsPositive() {
            // act & assert
            assertDoesNotThrow(() -> new Price(150000L));
        }

        @DisplayName("가격이 0이면, 정상 생성된다.")
        @Test
        void createsPrice_whenValueIsZero() {
            // act & assert
            assertDoesNotThrow(() -> new Price(0L));
        }

        @DisplayName("가격이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> new Price(null));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> new Price(-1L));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
