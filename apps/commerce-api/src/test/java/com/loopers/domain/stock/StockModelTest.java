package com.loopers.domain.stock;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelTest {

    private ProductModel product;

    @BeforeEach
    void setUp() {
        BrandModel brand = new BrandModel("Nike", "스포츠 브랜드");
        product = new ProductModel(brand, "나이키 에어맥스", 150_000);
    }

    @DisplayName("StockModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성 시 필드가 정상 설정된다.")
        @Test
        void createsStockModel_whenAllFieldsAreValid() {
            StockModel stock = new StockModel(product, 100);
            assertThat(stock.getQuantity()).isEqualTo(100);
        }

        @DisplayName("재고 0으로 생성 시 정상 생성된다.")
        @Test
        void createsStockModel_whenQuantityIsZero() {
            StockModel stock = new StockModel(product, 0);
            assertThat(stock.getQuantity()).isEqualTo(0);
        }

        @DisplayName("null 상품으로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                new StockModel(null, 10)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("음수 재고로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException result = assertThrows(CoreException.class, () ->
                new StockModel(product, -1)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("decrease()를 호출할 때,")
    @Nested
    class Decrease {

        @DisplayName("유효한 수량만큼 차감 시 재고가 감소한다.")
        @Test
        void decreasesQuantity_whenAmountIsValid() {
            StockModel stock = new StockModel(product, 10);
            stock.decrease(3);
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("전체 재고 수량만큼 차감 시 재고가 0이 된다.")
        @Test
        void decreasesToZero_whenAmountEqualsQuantity() {
            StockModel stock = new StockModel(product, 5);
            stock.decrease(5);
            assertThat(stock.getQuantity()).isEqualTo(0);
        }

        @DisplayName("재고보다 많은 수량 차감 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountExceedsQuantity() {
            StockModel stock = new StockModel(product, 3);
            CoreException result = assertThrows(CoreException.class, () ->
                stock.decrease(4)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("0 이하의 차감 수량 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsZeroOrNegative() {
            StockModel stock = new StockModel(product, 10);
            CoreException result = assertThrows(CoreException.class, () ->
                stock.decrease(0)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
