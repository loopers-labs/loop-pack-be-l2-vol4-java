package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private ProductModel product(int stock) {
        return new ProductModel(1L, "상품", "설명", 1_000L, stock);
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("브랜드가 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException result =
                assertThrows(CoreException.class, () -> new ProductModel(null, "상품", "설명", 1_000L, 10));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNegative() {
            CoreException result =
                assertThrows(CoreException.class, () -> new ProductModel(1L, "상품", "설명", 1_000L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DeductStock {
        @DisplayName("재고가 충분하면 수량만큼 차감된다.")
        @Test
        void deducts_whenStockIsEnough() {
            ProductModel product = product(10);
            product.deductStock(3);
            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고보다 많은 수량을 차감하면 CONFLICT 예외가 발생하고 재고는 변하지 않는다.")
        @Test
        void throwsConflict_whenStockIsInsufficient() {
            ProductModel product = product(2);
            CoreException result = assertThrows(CoreException.class, () -> product.deductStock(3));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(product.getStock()).isEqualTo(2);
        }

        @DisplayName("0 이하 수량을 차감하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            ProductModel product = product(10);
            CoreException result = assertThrows(CoreException.class, () -> product.deductStock(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고를 정확히 모두 차감하면 0이 되며 음수가 되지 않는다.")
        @Test
        void allowsExactDeduction_neverNegative() {
            ProductModel product = product(5);
            product.deductStock(5);
            assertThat(product.getStock()).isZero();
        }
    }

    @DisplayName("재고를 복구할 때, 차감된 재고가 수량만큼 증가한다.")
    @Test
    void restoresStock() {
        ProductModel product = product(5);
        product.deductStock(5);
        product.restoreStock(2);
        assertThat(product.getStock()).isEqualTo(2);
    }
}
