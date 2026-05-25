package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelTest {

    private static final Long VALID_PRODUCT_ID = 1L;

    @DisplayName("재고 모델 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 productId와 0 이상의 수량을 입력하면 재고가 정상 생성된다")
        @Test
        void createsStock_whenAllFieldsAreValid() {
            // given
            // when
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 10);

            // then
            assertAll(
                () -> assertThat(stock.getProductId()).isEqualTo(VALID_PRODUCT_ID),
                () -> assertThat(stock.getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("초기 수량이 0이면 재고가 정상 생성된다")
        @Test
        void createsStock_whenInitialQuantityIsZero() {
            // when
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 0);

            // then
            assertThat(stock.getQuantity()).isZero();
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // when
            CoreException ex = assertThrows(CoreException.class, () -> new StockModel(null, 10));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // when
            CoreException ex = assertThrows(CoreException.class, () -> new StockModel(VALID_PRODUCT_ID, null));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("초기 수량이 음수이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // when
            CoreException ex = assertThrows(CoreException.class, () -> new StockModel(VALID_PRODUCT_ID, -1));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 차감 시")
    @Nested
    class Decrease {

        @DisplayName("현재 재고보다 적은 수량을 차감하면 정상 차감된다")
        @Test
        void decreasesStock_whenAmountIsLessThanQuantity() {
            // given
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 10);

            // when
            stock.decrease(3);

            // then
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("현재 재고와 동일한 수량을 차감하면 재고가 0이 된다")
        @Test
        void decreasesStockToZero_whenAmountEqualsQuantity() {
            // given
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 5);

            // when
            stock.decrease(5);

            // then
            assertThat(stock.getQuantity()).isZero();
        }

        @DisplayName("현재 재고보다 많은 수량을 차감하면 CONFLICT 예외가 발생하고 재고는 변경되지 않는다")
        @Test
        void throwsConflict_whenAmountExceedsQuantity() {
            // given
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 5);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> stock.decrease(6));

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(stock.getQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("차감 수량이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenAmountIsZeroOrNegative() {
            // given
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 10);

            // when
            CoreException zero = assertThrows(CoreException.class, () -> stock.decrease(0));
            CoreException negative = assertThrows(CoreException.class, () -> stock.decrease(-1));

            // then
            assertAll(
                () -> assertThat(zero.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(negative.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("재고 증가 시")
    @Nested
    class Increase {

        @DisplayName("양수를 더하면 재고가 정상 증가한다")
        @Test
        void increasesStock_whenAmountIsPositive() {
            // given
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 10);

            // when
            stock.increase(5);

            // then
            assertThat(stock.getQuantity()).isEqualTo(15);
        }

        @DisplayName("증가 수량이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenAmountIsZeroOrNegative() {
            // given
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 10);

            // when
            CoreException zero = assertThrows(CoreException.class, () -> stock.increase(0));
            CoreException negative = assertThrows(CoreException.class, () -> stock.increase(-1));

            // then
            assertAll(
                () -> assertThat(zero.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(negative.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("구매 가능 여부는")
    @Nested
    class IsAvailable {

        @DisplayName("재고가 1 이상이면 true를 반환한다")
        @Test
        void returnsTrue_whenQuantityIsPositive() {
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 1);
            assertThat(stock.isAvailable()).isTrue();
        }

        @DisplayName("재고가 0이면 false를 반환한다")
        @Test
        void returnsFalse_whenQuantityIsZero() {
            StockModel stock = new StockModel(VALID_PRODUCT_ID, 0);
            assertThat(stock.isAvailable()).isFalse();
        }
    }
}
