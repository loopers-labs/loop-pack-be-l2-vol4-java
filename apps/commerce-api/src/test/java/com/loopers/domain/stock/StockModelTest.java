package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelTest {

    private static final Long PRODUCT_ID = 10L;

    @Nested
    @DisplayName("Stock 생성")
    class Create {

        @DisplayName("0 이상의 값으로 생성된다 (0 경계 포함)")
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 100})
        void given_nonNegative_when_create_then_creates(int value) {
            StockModel stock = new StockModel(PRODUCT_ID, value);
            assertAll(
                    () -> assertThat(stock.getQuantity()).isEqualTo(value),
                    () -> assertThat(stock.getProductId()).isEqualTo(PRODUCT_ID)
            );
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullProductId_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class, () -> new StockModel(null, 10));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {-1, -100})
        void given_negative_when_create_then_throwsBadRequest(int value) {
            CoreException result = assertThrows(CoreException.class, () -> new StockModel(PRODUCT_ID, value));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("차감 (deduct)")
    class Deduct {

        @DisplayName("충분하면 재고가 차감된다")
        @Test
        void given_enough_when_deduct_then_decreased() {
            StockModel stock = new StockModel(PRODUCT_ID, 10);
            stock.deduct(3);
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고와 동일 수량을 차감하면 0이 된다 (경계)")
        @Test
        void given_exact_when_deduct_then_zero() {
            StockModel stock = new StockModel(PRODUCT_ID, 5);
            stock.deduct(5);
            assertThat(stock.getQuantity()).isEqualTo(0);
        }

        @DisplayName("재고보다 많으면 CONFLICT 예외가 발생하고 재고는 변하지 않는다")
        @Test
        void given_insufficient_when_deduct_then_throwsConflict() {
            StockModel stock = new StockModel(PRODUCT_ID, 2);
            CoreException result = assertThrows(CoreException.class, () -> stock.deduct(3));
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                    () -> assertThat(stock.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("차감 수량이 0 이하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void given_nonPositive_when_deduct_then_throwsBadRequest(int amount) {
            StockModel stock = new StockModel(PRODUCT_ID, 10);
            CoreException result = assertThrows(CoreException.class, () -> stock.deduct(amount));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("복원 (restore)")
    class Restore {

        @DisplayName("복원하면 재고가 증가한다")
        @Test
        void given_quantity_when_restore_then_increased() {
            StockModel stock = new StockModel(PRODUCT_ID, 5);
            stock.restore(3);
            assertThat(stock.getQuantity()).isEqualTo(8);
        }

        @DisplayName("복원 수량이 0 이하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void given_nonPositive_when_restore_then_throwsBadRequest(int amount) {
            StockModel stock = new StockModel(PRODUCT_ID, 5);
            CoreException result = assertThrows(CoreException.class, () -> stock.restore(amount));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("가용성 (isAvailable)")
    class IsAvailable {

        @DisplayName("재고가 요청 수량 이상이면 true, 미만이면 false")
        @Test
        void given_stock_when_isAvailable_then_comparesQuantity() {
            StockModel stock = new StockModel(PRODUCT_ID, 5);
            assertAll(
                    () -> assertThat(stock.isAvailable(5)).isTrue(),
                    () -> assertThat(stock.isAvailable(3)).isTrue(),
                    () -> assertThat(stock.isAvailable(6)).isFalse()
            );
        }
    }
}
