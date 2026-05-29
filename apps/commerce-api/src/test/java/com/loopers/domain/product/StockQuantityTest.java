package com.loopers.domain.product;

import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockQuantityTest {

    @DisplayName("재고 차감 시,")
    @Nested
    class Decrease {

        @DisplayName("재고가 충분하면, 차감된 수량을 반환한다.")
        @Test
        void returnsDecreased_whenStockIsSufficient() {
            StockQuantity stock = new StockQuantity(10);

            StockQuantity result = stock.decrease(3);

            assertThat(result.getValue()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            StockQuantity stock = new StockQuantity(2);

            CoreException exception = assertThrows(CoreException.class, () -> stock.decrease(5));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정확히 재고 수량만큼 차감하면, 0이 된다.")
        @Test
        void returnsZero_whenDecreaseEqualsStock() {
            StockQuantity stock = new StockQuantity(5);

            StockQuantity result = stock.decrease(5);

            assertThat(result.getValue()).isEqualTo(0);
        }
    }

    @DisplayName("재고 증가 시,")
    @Nested
    class Increase {

        @DisplayName("유효한 증가량이면, 증가된 수량을 반환한다.")
        @Test
        void returnsIncreased_whenAmountIsValid() {
            StockQuantity stock = new StockQuantity(5);

            StockQuantity result = stock.increase(3);

            assertThat(result.getValue()).isEqualTo(8);
        }
    }
}
