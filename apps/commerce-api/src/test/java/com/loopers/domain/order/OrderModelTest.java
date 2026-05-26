package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 정보를 주면, 상태가 PENDING으로 생성된다.")
        @Test
        void createsOrder_withPendingStatus_whenValidInfoIsProvided() {
            Order order = new Order(1L, BigDecimal.valueOf(50000));

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING)
            );
        }

        @DisplayName("유저 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Order(null, BigDecimal.valueOf(50000)));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("총 주문 금액이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalPriceIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Order(1L, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("총 주문 금액이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalPriceIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Order(1L, BigDecimal.valueOf(-1)));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("총 주문 금액이 0이면, 정상적으로 생성된다.")
        @Test
        void createsOrder_whenTotalPriceIsZero() {
            Order order = new Order(1L, BigDecimal.ZERO);

            assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @DisplayName("주문 상태를 변경할 때,")
    @Nested
    class ChangeStatus {

        @DisplayName("confirm() 하면, 상태가 CONFIRMED가 된다.")
        @Test
        void confirmsOrder_whenConfirmIsCalled() {
            Order order = new Order(1L, BigDecimal.valueOf(50000));

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("cancel() 하면, 상태가 CANCELED가 된다.")
        @Test
        void cancelsOrder_whenCancelIsCalled() {
            Order order = new Order(1L, BigDecimal.valueOf(50000));

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }
    }

    @DisplayName("주문 상품을 생성할 때,")
    @Nested
    class CreateOrderItem {

        @DisplayName("유효한 정보를 주면, 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenValidInfoIsProvided() {
            OrderItem item = new OrderItem(1L, 10L, "청바지", BigDecimal.valueOf(50000), 3);

            assertAll(
                () -> assertThat(item.getProductName()).isEqualTo("청바지"),
                () -> assertThat(item.getProductPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000)),
                () -> assertThat(item.getQuantity()).isEqualTo(3)
            );
        }

        @DisplayName("주문 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItem(null, 10L, "청바지", BigDecimal.valueOf(50000), 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItem(1L, null, "청바지", BigDecimal.valueOf(50000), 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 10L, "  ", BigDecimal.valueOf(50000), 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductPriceIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 10L, "청바지", BigDecimal.valueOf(-1), 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 10L, "청바지", BigDecimal.valueOf(50000), 0));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 10L, "청바지", BigDecimal.valueOf(50000), -1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
