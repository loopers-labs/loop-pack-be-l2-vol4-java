package com.loopers.domain.stock;

import com.loopers.domain.stock.model.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockTest {

    @DisplayName("재고를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsStock_whenAllFieldsAreValid() {
            // Arrange & Act
            Stock stock = Stock.create(1L, 100);

            // Assert
            assertThat(stock.getProductId()).isEqualTo(1L);
            assertThat(stock.getQuantity()).isEqualTo(100);
        }

        @DisplayName("수량이 0이면, 정상적으로 생성된다.")
        @Test
        void createsStock_whenQuantityIsZero() {
            Stock stock = Stock.create(1L, 0);
            assertThat(stock.getQuantity()).isZero();
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException result = assertThrows(CoreException.class, () ->
                Stock.create(1L, -1)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                Stock.create(null, 10)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 가용 여부를 확인할 때, ")
    @Nested
    class IsAvailable {

        @DisplayName("요청 수량이 현재 재고 이하이면, true를 반환한다.")
        @Test
        void returnsTrue_whenRequestedQuantityIsWithinStock() {
            Stock stock = Stock.create(1L, 10);
            assertThat(stock.isAvailable(10)).isTrue();
        }

        @DisplayName("요청 수량이 현재 재고를 초과하면, false를 반환한다.")
        @Test
        void returnsFalse_whenRequestedQuantityExceedsStock() {
            Stock stock = Stock.create(1L, 10);
            assertThat(stock.isAvailable(11)).isFalse();
        }

        @DisplayName("요청 수량이 0이면, true를 반환한다.")
        @Test
        void returnsTrue_whenRequestedQuantityIsZero() {
            Stock stock = Stock.create(1L, 0);
            assertThat(stock.isAvailable(0)).isTrue();
        }
    }
}
