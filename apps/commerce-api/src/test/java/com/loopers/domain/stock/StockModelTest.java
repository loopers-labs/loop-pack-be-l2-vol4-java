package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockModelTest {

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 productId와 quantity(0 이상)로 생성하면, StockModel이 정상 생성된다.")
        @Test
        void createsStock_whenAllFieldsAreValid() {
            // arrange
            Long productId = 1L;
            int quantity = 10;

            // act
            StockModel stock = new StockModel(productId, quantity);

            // assert
            assertThat(stock.getProductId()).isEqualTo(productId);
            assertThat(stock.getQuantity()).isEqualTo(quantity);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act & assert
            assertThatThrownBy(() -> new StockModel(null, 10))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // act & assert
            assertThatThrownBy(() -> new StockModel(1L, -1))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {

        @DisplayName("재고가 충분할 때 decrease를 호출하면, quantity가 차감된다.")
        @Test
        void decreasesQuantity_whenStockIsSufficient() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act
            stock.decrease(3);

            // assert
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고와 동일한 수량으로 decrease를 호출하면, quantity가 0이 된다.")
        @Test
        void decreasesToZero_whenQuantityEqualsStock() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act
            stock.decrease(10);

            // assert
            assertThat(stock.getQuantity()).isZero();
        }

        @DisplayName("재고보다 많은 수량으로 decrease를 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExceedsStock() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act & assert
            assertThatThrownBy(() -> stock.decrease(11))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 증가할 때,")
    @Nested
    class Increase {

        @DisplayName("increase를 호출하면, quantity가 늘어난다.")
        @Test
        void increasesQuantity_whenCalled() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act
            stock.increase(5);

            // assert
            assertThat(stock.getQuantity()).isEqualTo(15);
        }
    }

    @DisplayName("재고 가용 여부를 확인할 때,")
    @Nested
    class IsAvailable {

        @DisplayName("재고가 요청 수량 이상이면, true를 반환한다.")
        @Test
        void returnsTrue_whenStockIsSufficient() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act & assert
            assertThat(stock.isAvailable(10)).isTrue();
        }

        @DisplayName("재고가 요청 수량 미만이면, false를 반환한다.")
        @Test
        void returnsFalse_whenStockIsInsufficient() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act & assert
            assertThat(stock.isAvailable(11)).isFalse();
        }
    }
}
