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

        @DisplayName("유효한 정보를 주면, 상태가 PENDING으로 생성되고 totalPrice는 originalPrice - discountAmount이다.")
        @Test
        void createsOrder_withPendingStatus_whenValidInfoIsProvided() {
            Order order = new Order(1L, null, BigDecimal.valueOf(50000), BigDecimal.valueOf(5000));

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000)),
                () -> assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000)),
                () -> assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(45000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING)
            );
        }

        @DisplayName("할인 금액이 0이면, totalPrice는 originalPrice와 동일하다.")
        @Test
        void createsOrder_withNoDiscount_whenDiscountAmountIsZero() {
            Order order = new Order(1L, null, BigDecimal.valueOf(50000), BigDecimal.ZERO);

            assertAll(
                () -> assertThat(order.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000)),
                () -> assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                () -> assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000))
            );
        }

        @DisplayName("유저 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Order(null, null, BigDecimal.valueOf(50000), BigDecimal.ZERO));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 금액이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOriginalPriceIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Order(1L, null, null, BigDecimal.ZERO));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 금액이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOriginalPriceIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Order(1L, null, BigDecimal.valueOf(-1), BigDecimal.ZERO));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 금액이 주문 금액을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDiscountAmountExceedsOriginalPrice() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Order(1L, null, BigDecimal.valueOf(1000), BigDecimal.valueOf(1001)));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 금액이 0이면, 정상적으로 생성된다.")
        @Test
        void createsOrder_whenOriginalPriceIsZero() {
            Order order = new Order(1L, null, BigDecimal.ZERO, BigDecimal.ZERO);

            assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @DisplayName("주문 상태를 변경할 때,")
    @Nested
    class ChangeStatus {

        @DisplayName("confirm() 하면, 상태가 CONFIRMED가 된다.")
        @Test
        void confirmsOrder_whenConfirmIsCalled() {
            Order order = new Order(1L, null, BigDecimal.valueOf(50000), BigDecimal.ZERO);

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("cancel() 하면, 상태가 CANCELED가 된다.")
        @Test
        void cancelsOrder_whenCancelIsCalled() {
            Order order = new Order(1L, null, BigDecimal.valueOf(50000), BigDecimal.ZERO);

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }
    }

    @DisplayName("주문 상품을 생성할 때,")
    @Nested
    class CreateOrderItem {

        @DisplayName("유효한 정보를 주면, orderId 없이 정상적으로 생성된다.")
        @Test
        void createsOrderItem_withoutOrderId_whenValidInfoIsProvided() {
            OrderItem item = new OrderItem(10L, "청바지", BigDecimal.valueOf(50000), 3);

            assertAll(
                () -> assertThat(item.getOrderId()).isNull(),
                () -> assertThat(item.getProductName()).isEqualTo("청바지"),
                () -> assertThat(item.getProductPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000)),
                () -> assertThat(item.getQuantity()).isEqualTo(3)
            );
        }

        @DisplayName("withOrderId()를 호출하면, orderId가 설정된 새 OrderItem이 반환된다.")
        @Test
        void returnsNewOrderItemWithOrderId_whenWithOrderIdIsCalled() {
            OrderItem item = new OrderItem(10L, "청바지", BigDecimal.valueOf(50000), 3);

            OrderItem itemWithOrderId = item.withOrderId(1L);

            assertAll(
                () -> assertThat(itemWithOrderId.getOrderId()).isEqualTo(1L),
                () -> assertThat(itemWithOrderId.getProductName()).isEqualTo("청바지"),
                () -> assertThat(itemWithOrderId.getProductPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000)),
                () -> assertThat(itemWithOrderId.getQuantity()).isEqualTo(3)
            );
        }
    }
}
