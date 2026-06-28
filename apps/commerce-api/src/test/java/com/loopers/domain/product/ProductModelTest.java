package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ProductModelTest {

    private ProductModel createProduct(int stock) {
        return new ProductModel("상품명", "설명", 10000L, stock, 1L);
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DecreaseStock {

        @DisplayName("재고보다 적은 수량이면 차감된다.")
        @Test
        void decreasesStock_whenQuantityIsValid() {
            ProductModel product = createProduct(10);
            product.decreaseStock(3);
            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고와 동일한 수량이면 0이 된다.")
        @Test
        void setsStockToZero_whenQuantityEqualsStock() {
            ProductModel product = createProduct(5);
            product.decreaseStock(5);
            assertThat(product.getStock()).isEqualTo(0);
        }

        @DisplayName("재고보다 많은 수량이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExceedsStock() {
            ProductModel product = createProduct(3);
            CoreException ex = assertThrows(CoreException.class, () -> product.decreaseStock(5));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZeroOrNegative() {
            ProductModel product = createProduct(10);
            CoreException ex = assertThrows(CoreException.class, () -> product.decreaseStock(0));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 카운트를 변경할 때,")
    @Nested
    class LikeCount {

        @DisplayName("incrementLikeCount 호출 시 1 증가한다.")
        @Test
        void incrementsLikeCount() {
            ProductModel product = createProduct(10);
            product.incrementLikeCount();
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("decrementLikeCount 호출 시 1 감소한다.")
        @Test
        void decrementsLikeCount() {
            ProductModel product = createProduct(10);
            product.incrementLikeCount();
            product.decrementLikeCount();
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요 수가 0일 때 decrementLikeCount 호출하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDecrementingZeroLikeCount() {
            ProductModel product = createProduct(10);
            CoreException ex = assertThrows(CoreException.class, product::decrementLikeCount);
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 상태(stockStatus)를 조회할 때,")
    @Nested
    class StockStatusTest {

        @DisplayName("재고가 0이면 SOLD_OUT이다.")
        @Test
        void returnsSoldOut_whenStockIsZero() {
            assertThat(createProduct(0).stockStatus()).isEqualTo(StockStatus.SOLD_OUT);
        }

        @DisplayName("재고가 1 이상이면 ON_SALE이다.")
        @Test
        void returnsOnSale_whenStockIsPositive() {
            assertThat(createProduct(1).stockStatus()).isEqualTo(StockStatus.ON_SALE);
        }
    }
}
