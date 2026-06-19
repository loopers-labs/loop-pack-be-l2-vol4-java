package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 값이 주어지면, PENDING 상태로 생성된다.")
        @Test
        void createsOrder_withPendingStatus_whenValid() {
            OrderModel order = new OrderModel(1L, 30000L, 0L, 30000L, null);

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getOriginalPrice()).isEqualTo(30000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(0L),
                () -> assertThat(order.getFinalPrice()).isEqualTo(30000L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING)
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderModel(null, 30000L, 0L, 30000L, null)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("originalPrice가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalPriceIsNegative() {
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderModel(1L, -1L, 0L, -1L, null)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 아이템을 생성할 때, ")
    @Nested
    class CreateOrderItem {

        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenValid() {
            OrderItemModel item = new OrderItemModel(1L, 2L, "에어맥스 90", 159000L, 2);

            assertAll(
                () -> assertThat(item.getOrderId()).isEqualTo(1L),
                () -> assertThat(item.getProductId()).isEqualTo(2L),
                () -> assertThat(item.getProductName()).isEqualTo("에어맥스 90"),
                () -> assertThat(item.getUnitPrice()).isEqualTo(159000L),
                () -> assertThat(item.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("quantity가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, 2L, "에어맥스 90", 159000L, 0)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, 2L, "에어맥스 90", 159000L, -1)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, 2L, "  ", 159000L, 1)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
