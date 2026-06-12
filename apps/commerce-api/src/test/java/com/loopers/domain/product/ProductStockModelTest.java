package com.loopers.domain.product;

import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductStockModelTest {

    private static ProductStockModel stockOf(long price, int quantity) {
        ProductModel product = new ProductModel(1L, new ProductName("테스트상품"));
        return new ProductStockModel(product, new Price(price), quantity);
    }

    @DisplayName("Price -")
    @Nested
    class PriceTest {

        @DisplayName("가격 생성 시,")
        @Nested
        class Create {

            @DisplayName("0 이상이면, 정상 생성된다.")
            @Test
            void createsPrice_whenValueIsNonNegative() {
                assertThat(new Price(0L).getValue()).isEqualTo(0L);
                assertThat(new Price(10000L).getValue()).isEqualTo(10000L);
            }

            @DisplayName("음수이면, BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenValueIsNegative() {
                CoreException exception = assertThrows(CoreException.class, () -> new Price(-1L));

                assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

    }

}
