package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductStockTest {

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("0 이상의 재고면 정상 생성된다.")
        @Test
        void createsStock_whenStockIsZeroOrAbove() {
            ProductStock stock = new ProductStock(1L, 10);
            assertThat(stock.getStock()).isEqualTo(10);
        }

        @DisplayName("재고가 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNegative() {
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductStock(1L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고 0으로 생성할 수 있다.")
        @Test
        void createsStock_whenStockIsZero() {
            ProductStock stock = new ProductStock(1L, 0);
            assertThat(stock.getStock()).isEqualTo(0);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {

        @DisplayName("재고가 충분하면 정상 차감된다.")
        @Test
        void decreasesStock_whenStockIsSufficient() {
            ProductStock stock = new ProductStock(1L, 10);
            stock.decrease(3);
            assertThat(stock.getStock()).isEqualTo(7);
        }

        @DisplayName("재고와 동일한 수량을 차감하면 0이 된다.")
        @Test
        void decreasesStockToZero_whenQuantityEqualsStock() {
            ProductStock stock = new ProductStock(1L, 5);
            stock.decrease(5);
            assertThat(stock.getStock()).isEqualTo(0);
        }

        @DisplayName("재고보다 많은 수량을 차감하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExceedsStock() {
            ProductStock stock = new ProductStock(1L, 3);
            CoreException result = assertThrows(CoreException.class,
                () -> stock.decrease(5));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 0일 때 차감하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsZero() {
            ProductStock stock = new ProductStock(1L, 0);
            CoreException result = assertThrows(CoreException.class,
                () -> stock.decrease(1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            ProductStock stock = new ProductStock(1L, 10);
            CoreException result = assertThrows(CoreException.class,
                () -> stock.decrease(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 복원할 때,")
    @Nested
    class Restore {

        @DisplayName("정상 수량이면 재고가 증가한다.")
        @Test
        void restoresStock_whenQuantityIsValid() {
            ProductStock stock = new ProductStock(1L, 5);
            stock.restore(3);
            assertThat(stock.getStock()).isEqualTo(8);
        }

        @DisplayName("복원 수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            ProductStock stock = new ProductStock(1L, 5);
            CoreException result = assertThrows(CoreException.class,
                () -> stock.restore(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
