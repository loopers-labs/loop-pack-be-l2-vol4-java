package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockTest {

    @DisplayName("Stock을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("양수 재고이면, 정상 생성된다.")
        @Test
        void createsStock_whenValueIsPositive() {
            // act & assert
            assertDoesNotThrow(() -> new Stock(10));
        }

        @DisplayName("재고가 0이면, 정상 생성된다.")
        @Test
        void createsStock_whenValueIsZero() {
            // act & assert
            assertDoesNotThrow(() -> new Stock(0));
        }

        @DisplayName("재고가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> new Stock(null));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> new Stock(-1));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {

        @DisplayName("재고가 충분하면, 차감된 새 Stock을 반환한다.")
        @Test
        void returnsDecreasedStock_whenStockIsSufficient() {
            // arrange
            Stock stock = new Stock(10);

            // act
            Stock result = stock.decrease(3);

            // assert
            assertThat(result.value()).isEqualTo(7);
        }

        @DisplayName("재고가 요청 수량과 정확히 같으면, 0인 Stock을 반환한다.")
        @Test
        void returnsZeroStock_whenStockEqualsQuantity() {
            // arrange
            Stock stock = new Stock(5);

            // act
            Stock result = stock.decrease(5);

            // assert
            assertThat(result.value()).isEqualTo(0);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            Stock stock = new Stock(3);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.decrease(5));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // arrange
            Stock stock = new Stock(10);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.decrease(0));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // arrange
            Stock stock = new Stock(10);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.decrease(-1));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
