package com.loopers.domain.product;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    private static Product sampleProduct(int stock) {
        return Product.create("티셔츠", "편안한 면 티셔츠", Money.of(10_000L), stock, 1L);
    }

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("재고가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNegative() {
            CoreException result = assertThrows(CoreException.class,
                () -> Product.create("티셔츠", "설명", Money.of(10_000L), -1, 1L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드가 지정되지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> Product.create("티셔츠", "설명", Money.of(10_000L), 10, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 값이면, 상품이 생성된다.")
        @Test
        void createsProduct_whenValid() {
            Product product = sampleProduct(10);

            assertThat(product.getName()).isEqualTo("티셔츠");
            assertThat(product.getPrice()).isEqualTo(Money.of(10_000L));
            assertThat(product.getStock()).isEqualTo(10);
            assertThat(product.getBrandId()).isEqualTo(1L);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DecreaseStock {

        @DisplayName("재고가 충분하면, 요청 수량만큼 차감된다.")
        @Test
        void decreasesStock_whenEnough() {
            Product product = sampleProduct(10);

            product.decreaseStock(3);

            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고보다 많은 수량을 차감하면, CONFLICT 예외가 발생하고 재고는 음수가 되지 않는다.")
        @Test
        void throwsConflict_whenStockIsNotEnough() {
            Product product = sampleProduct(2);

            CoreException result = assertThrows(CoreException.class, () -> product.decreaseStock(3));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(product.getStock()).isEqualTo(2); // 변하지 않음 (음수 방지)
        }

        @DisplayName("차감 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            Product product = sampleProduct(10);

            CoreException result = assertThrows(CoreException.class, () -> product.decreaseStock(0));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 가능 여부를 판단할 때, ")
    @Nested
    class IsOrderable {

        @DisplayName("재고가 요청 수량 이상이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenStockIsEnough() {
            Product product = sampleProduct(5);

            assertThat(product.isOrderable(5)).isTrue();
            assertThat(product.isOrderable(6)).isFalse();
        }
    }
}
