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

    @DisplayName("StockModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsStockModel_whenAllFieldsAreValid() {
            // given
            Long productId = 1L;
            Integer quantity = 10;

            // when
            StockModel stock = new StockModel(productId, quantity);

            // then
            assertAll(
                () -> assertThat(stock.getProductId()).isEqualTo(productId),
                () -> assertThat(stock.getQuantity()).isEqualTo(quantity)
            );
        }

        @DisplayName("productId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // given
            Long productId = null;
            Integer quantity = 10;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new StockModel(productId, quantity));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 ID 는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("quantity 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNull() {
            // given
            Long productId = 1L;
            Integer quantity = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new StockModel(productId, quantity));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("재고는 0 이상이어야 합니다.")
            );
        }

        @DisplayName("quantity 가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNegative() {
            // given
            Long productId = 1L;
            Integer quantity = -1;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new StockModel(productId, quantity));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("재고는 0 이상이어야 합니다.")
            );
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class Decrease {

        @DisplayName("잔량보다 작거나 같은 amount 가 주어지면, 잔량이 amount 만큼 줄어든다.")
        @Test
        void decreasesQuantity_whenAmountIsLessThanOrEqualToCurrent() {
            // given
            StockModel stock = new StockModel(1L, 10);

            // when
            stock.decrease(3);

            // then
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("잔량과 같은 amount 가 주어지면, 잔량이 0 이 된다.")
        @Test
        void decreasesToZero_whenAmountEqualsCurrent() {
            // given
            StockModel stock = new StockModel(1L, 5);

            // when
            stock.decrease(5);

            // then
            assertThat(stock.getQuantity()).isEqualTo(0);
        }

        @DisplayName("잔량보다 큰 amount 가 주어지면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenAmountIsGreaterThanCurrent() {
            // given
            StockModel stock = new StockModel(1L, 5);

            // when
            CoreException result = assertThrows(CoreException.class, () -> stock.decrease(6));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(result.getCustomMessage()).isEqualTo("재고가 부족합니다.")
            );
        }

        @DisplayName("amount 가 0 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsZero() {
            // given
            StockModel stock = new StockModel(1L, 10);

            // when
            CoreException result = assertThrows(CoreException.class, () -> stock.decrease(0));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("차감 수량은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("amount 가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNegative() {
            // given
            StockModel stock = new StockModel(1L, 10);

            // when
            CoreException result = assertThrows(CoreException.class, () -> stock.decrease(-1));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("차감 수량은 1 이상이어야 합니다.")
            );
        }
    }

    @DisplayName("재고를 증가시킬 때, ")
    @Nested
    class Increase {

        @DisplayName("양수 amount 가 주어지면, 잔량이 amount 만큼 늘어난다.")
        @Test
        void increasesQuantity_whenAmountIsPositive() {
            // given
            StockModel stock = new StockModel(1L, 10);

            // when
            stock.increase(5);

            // then
            assertThat(stock.getQuantity()).isEqualTo(15);
        }

        @DisplayName("amount 가 0 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsZero() {
            // given
            StockModel stock = new StockModel(1L, 10);

            // when
            CoreException result = assertThrows(CoreException.class, () -> stock.increase(0));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("증가 수량은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("amount 가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNegative() {
            // given
            StockModel stock = new StockModel(1L, 10);

            // when
            CoreException result = assertThrows(CoreException.class, () -> stock.increase(-1));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("증가 수량은 1 이상이어야 합니다.")
            );
        }
    }

    @DisplayName("재고 가용 여부를 확인할 때, ")
    @Nested
    class IsAvailable {

        @DisplayName("quantity 가 0 보다 크면, true 를 반환한다.")
        @Test
        void returnsTrue_whenQuantityIsPositive() {
            // given
            StockModel stock = new StockModel(1L, 1);

            // when
            boolean result = stock.isAvailable();

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("quantity 가 0 이면, false 를 반환한다.")
        @Test
        void returnsFalse_whenQuantityIsZero() {
            // given
            StockModel stock = new StockModel(1L, 0);

            // when
            boolean result = stock.isAvailable();

            // then
            assertThat(result).isFalse();
        }
    }
}
