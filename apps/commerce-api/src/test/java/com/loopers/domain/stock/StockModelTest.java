package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


class StockModelTest {

    private static final Long PRODUCT_ID = 1L;

    @DisplayName("재고를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 입력으로 재고가 생성되면 productId와 quantity가 설정된다.")
        @Test
        void createsStockModel_withProductIdAndQuantity() {
            // given
            long quantity = 10L;

            // when
            StockModel stock = new StockModel(PRODUCT_ID, quantity);

            // then
            assertAll(
                    () -> assertThat(stock.getProductId()).isEqualTo(PRODUCT_ID),
                    () -> assertThat(stock.getQuantity()).isEqualTo(quantity)
            );
        }

        @DisplayName("수량이 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenQuantityIsNull(Long quantity) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new StockModel(PRODUCT_ID, quantity));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -10L})
        @ParameterizedTest
        void throwsBadRequest_whenQuantityIsNegative(long quantity) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new StockModel(PRODUCT_ID, quantity));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 여부를 확인할 때, ")
    @Nested
    class IsInStock {

        @DisplayName("수량이 1 이상이면 true 를 반환한다.")
        @ValueSource(longs = {1L, 10L})
        @ParameterizedTest
        void returnsTrue_whenQuantityIsPositive(long quantity) {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, quantity);

            // when
            boolean result = stock.isInStock();

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("수량이 0 이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenQuantityIsZero() {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, 0L);

            // when
            boolean result = stock.isInStock();

            // then
            assertThat(result).isFalse();
        }
    }

    @DisplayName("재고를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("정상 입력으로 수량이 수정된다.")
        @Test
        void updatesStockModel_withNewQuantity() {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, 10L);

            // when
            stock.update(20L);

            // then
            assertThat(stock.getQuantity()).isEqualTo(20L);
        }

        @DisplayName("수량이 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenNewQuantityIsNull(Long newQuantity) {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, 10L);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> stock.update(newQuantity));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -10L})
        @ParameterizedTest
        void throwsBadRequest_whenNewQuantityIsNegative(long newQuantity) {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, 10L);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> stock.update(newQuantity));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
