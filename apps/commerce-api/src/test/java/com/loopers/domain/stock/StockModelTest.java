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

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("0 이상의 수량으로 생성하면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 10, 1000})
        void createsStock_whenQuantityIsNonNegative(int quantity) {
            // act
            StockModel stock = StockModel.of(1L, quantity);

            // assert
            assertAll(
                () -> assertThat(stock.getProductId()).isEqualTo(1L),
                () -> assertThat(stock.getQuantity()).isEqualTo(quantity)
            );
        }

        @DisplayName("음수 수량으로 생성하면 BAD_REQUEST 예외가 발생한다 (Quantity VO 검증).")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException result = assertThrows(CoreException.class, () -> StockModel.of(1L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Deduct {

        @DisplayName("정상적으로 차감되어 수량이 감소한다.")
        @Test
        void deductsQuantity() {
            // arrange
            StockModel stock = StockModel.of(1L, 10);

            // act
            stock.deduct(3);

            // assert
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("차감 수량이 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {0})
        void throwsBadRequest_whenDeductingZero(int qty) {
            StockModel stock = StockModel.of(1L, 10);
            CoreException result = assertThrows(CoreException.class, () -> stock.deduct(qty));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고보다 많이 차감하려 하면 BAD_REQUEST 예외가 발생한다 (재고 음수 방지).")
        @Test
        void throwsBadRequest_whenInsufficientStock() {
            // arrange
            StockModel stock = StockModel.of(1L, 5);

            // act
            CoreException result = assertThrows(CoreException.class, () -> stock.deduct(10));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(stock.getQuantity()).isEqualTo(5);   // 원자성: 변경 없음
        }
    }

    @DisplayName("재고를 복구할 때,")
    @Nested
    class Restore {

        @DisplayName("정상적으로 복구되어 수량이 증가한다.")
        @Test
        void restoresQuantity() {
            StockModel stock = StockModel.of(1L, 5);
            stock.restore(3);
            assertThat(stock.getQuantity()).isEqualTo(8);
        }

        @DisplayName("복구 수량이 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRestoringZero() {
            StockModel stock = StockModel.of(1L, 5);
            CoreException result = assertThrows(CoreException.class, () -> stock.restore(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 표시 정책은,")
    @Nested
    class DisplayPolicy {

        @DisplayName("isAvailable: 1개 이상이면 true.")
        @Test
        void isAvailable() {
            assertAll(
                () -> assertThat(StockModel.of(1L, 1).isAvailable()).isTrue(),
                () -> assertThat(StockModel.of(1L, 0).isAvailable()).isFalse()
            );
        }

        @DisplayName("getDisplayQuantity: 10개 이하면 수량 노출, 초과면 null.")
        @Test
        void displayQuantityIs10OrLess() {
            assertAll(
                () -> assertThat(StockModel.of(1L, 10).getDisplayQuantity()).isEqualTo(10),
                () -> assertThat(StockModel.of(1L, 11).getDisplayQuantity()).isNull(),
                () -> assertThat(StockModel.of(1L, 0).getDisplayQuantity()).isZero()
            );
        }
    }

    @DisplayName("changeQuantity 로 절대값 변경 시,")
    @Nested
    class ChangeQuantity {

        @DisplayName("새 수량으로 갱신된다.")
        @Test
        void changes() {
            StockModel stock = StockModel.of(1L, 5);
            stock.changeQuantity(100);
            assertThat(stock.getQuantity()).isEqualTo(100);
        }

        @DisplayName("음수로 변경 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNegative() {
            StockModel stock = StockModel.of(1L, 5);
            CoreException result = assertThrows(CoreException.class, () -> stock.changeQuantity(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
