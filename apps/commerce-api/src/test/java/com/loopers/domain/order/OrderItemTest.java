package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @DisplayName("주문 항목 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 스냅샷 필드를 입력하면 정상 생성된다")
        @Test
        void createsItem_whenAllFieldsAreValid() {
            // when
            OrderItem item = new OrderItem(1L, "후드", 10_000L, 2);

            // then
            assertAll(
                () -> assertThat(item.getProductId()).isEqualTo(1L),
                () -> assertThat(item.getProductName()).isEqualTo("후드"),
                () -> assertThat(item.getUnitPrice().value()).isEqualTo(10_000L),
                () -> assertThat(item.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () -> new OrderItem(null, "후드", 10_000L, 1));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenProductNameIsNullOrBlank() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderItem(1L, null, 10_000L, 1)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderItem(1L, " ", 10_000L, 1)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }

        @DisplayName("단가가 null이거나 음수이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenUnitPriceIsNullOrNegative() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderItem(1L, "후드", null, 1)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderItem(1L, "후드", -1L, 1)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }

        @DisplayName("수량이 null이거나 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenQuantityIsNullOrNonPositive() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderItem(1L, "후드", 10_000L, null)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderItem(1L, "후드", 10_000L, 0)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderItem(1L, "후드", 10_000L, -1)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("주문 항목 소계 계산 시 단가 × 수량 결과를 반환한다")
    @Test
    void subtotal_isUnitPriceTimesQuantity() {
        OrderItem item = new OrderItem(1L, "후드", 10_000L, 3);
        assertThat(item.subtotal().value()).isEqualTo(30_000L);
    }
}
