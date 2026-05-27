package com.loopers.stock.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelTest {

    @DisplayName("StockModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 값이면, availableStock이 totalStock과 같다.")
        @Test
        void availableStockEqualsTotalStock_whenCreated() {
            // arrange & act
            StockModel stock = new StockModel(1L, 10);

            // assert
            assertThat(stock.availableStock()).isEqualTo(10);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> new StockModel(null, 10));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalStock이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalStockIsNegative() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> new StockModel(1L, -1));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 선점(reserve)할 때,")
    @Nested
    class Reserve {

        @DisplayName("가용 재고가 충분하면, reservedStock이 증가한다.")
        @Test
        void increasesReservedStock_whenAvailableStockIsSufficient() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act
            stock.reserve(3);

            // assert
            assertAll(
                () -> assertThat(stock.getReservedStock()).isEqualTo(3),
                () -> assertThat(stock.availableStock()).isEqualTo(7)
            );
        }

        @DisplayName("가용 재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAvailableStockIsInsufficient() {
            // arrange
            StockModel stock = new StockModel(1L, 5);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.reserve(8));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.reserve(0));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 확정(confirm)할 때,")
    @Nested
    class Confirm {

        @DisplayName("선점된 수량이 충분하면, totalStock과 reservedStock이 함께 감소한다.")
        @Test
        void decreasesTotalAndReservedStock_whenReservedStockIsSufficient() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            stock.reserve(5);

            // act
            stock.confirm(5);

            // assert
            assertAll(
                () -> assertThat(stock.getTotalStock()).isEqualTo(5),
                () -> assertThat(stock.getReservedStock()).isEqualTo(0)
            );
        }

        @DisplayName("선점된 수량보다 많이 확정하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenConfirmExceedsReserved() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            stock.reserve(3);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.confirm(5));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            stock.reserve(5);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.confirm(0));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 선점을 해제(release)할 때,")
    @Nested
    class Release {

        @DisplayName("선점된 수량이 충분하면, reservedStock이 감소한다.")
        @Test
        void decreasesReservedStock_whenReservedStockIsSufficient() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            stock.reserve(5);

            // act
            stock.release(3);

            // assert
            assertAll(
                () -> assertThat(stock.getReservedStock()).isEqualTo(2),
                () -> assertThat(stock.availableStock()).isEqualTo(8)
            );
        }

        @DisplayName("선점된 수량보다 많이 해제하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenReleaseExceedsReserved() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            stock.reserve(3);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.release(5));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            stock.reserve(5);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> stock.release(0));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
