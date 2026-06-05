package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductStockModelTest {

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 수량으로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsStock_whenValidQuantityIsProvided() {
            ProductStock stock = new ProductStock(1L, 100L);
            assertAll(
                () -> assertThat(stock.getProductId()).isEqualTo(1L),
                () -> assertThat(stock.getQuantity()).isEqualTo(100L)
            );
        }

        @DisplayName("수량이 0이면, 정상적으로 생성된다.")
        @Test
        void createsStock_whenQuantityIsZero() {
            ProductStock stock = new ProductStock(1L, 0L);
            assertThat(stock.getQuantity()).isEqualTo(0L);
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new ProductStock(1L, -1L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Deduct {

        @DisplayName("재고가 충분하면, 정상적으로 차감된다.")
        @Test
        void deductsStock_whenStockIsSufficient() {
            ProductStock stock = new ProductStock(1L, 10L);
            stock.deduct(3L);
            assertThat(stock.getQuantity()).isEqualTo(7L);
        }

        @DisplayName("재고가 정확히 소진되면, 0이 된다.")
        @Test
        void deductsStock_whenExactStockIsConsumed() {
            ProductStock stock = new ProductStock(1L, 5L);
            stock.deduct(5L);
            assertThat(stock.getQuantity()).isEqualTo(0L);
        }

        @DisplayName("재고가 부족하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenStockIsInsufficient() {
            ProductStock stock = new ProductStock(1L, 2L);
            CoreException ex = assertThrows(CoreException.class,
                () -> stock.deduct(3L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
