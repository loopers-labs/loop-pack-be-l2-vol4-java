package com.loopers.domain.order;

import com.loopers.domain.money.Money;
import com.loopers.domain.quantity.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {
    @DisplayName("주문을 접수할 때, ")
    @Nested
    class Place {
        @DisplayName("items의 lineAmount 합으로 totalAmount가 계산되고, 상태는 PENDING 다.")
        @Test
        void calculatesTotalAmount_fromItems() {
            // arrange
            Long userId = 1L;
            List<OrderItem> items = List.of(
                new OrderItem(10L, "에어맥스", new Money(BigDecimal.valueOf(1000)), new Quantity(2)),
                new OrderItem(11L, "양말", new Money(BigDecimal.valueOf(500)), new Quantity(1))
            );

            // act
            Order order = Order.place(userId, items);

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(userId),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getTotalAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500)),
                () -> assertThat(order.getDiscountAmount().getAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                () -> assertThat(order.getPaymentAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500))
            );
        }

        @DisplayName("할인 금액이 주어지면, 원금·할인 금액·최종 결제 금액이 모두 스냅샷된다.")
        @Test
        void snapshotsDiscountAndPaymentAmount_whenDiscountGiven() {
            // arrange
            Long userId = 1L;
            List<OrderItem> items = List.of(
                new OrderItem(10L, "에어맥스", new Money(BigDecimal.valueOf(1000)), new Quantity(2))
            );
            Money discountAmount = new Money(BigDecimal.valueOf(500));

            // act
            Order order = Order.place(userId, items, discountAmount);

            // assert
            assertAll(
                () -> assertThat(order.getTotalAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000)),
                () -> assertThat(order.getDiscountAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500)),
                () -> assertThat(order.getPaymentAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500))
            );
        }

        @DisplayName("items가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenItemsAreEmpty() {
            // arrange
            Long userId = 1L;
            List<OrderItem> items = List.of();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Order.place(userId, items);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 결과를 반영할 때, ")
    @Nested
    class Transition {
        private Order pendingOrder() {
            return Order.place(1L, List.of(
                new OrderItem(10L, "에어맥스", new Money(BigDecimal.valueOf(1000)), new Quantity(1))
            ));
        }

        @DisplayName("markPaid 는 PENDING 주문을 PAID 로 전이한다.")
        @Test
        void marksPaid_whenPending() {
            // arrange
            Order order = pendingOrder();

            // act
            order.markPaid();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("markFailed 는 PENDING 주문을 FAILED 로 전이한다.")
        @Test
        void marksFailed_whenPending() {
            // arrange
            Order order = pendingOrder();

            // act
            order.markFailed();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @DisplayName("이미 확정된 주문을 다시 전이하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyDecided() {
            // arrange
            Order order = pendingOrder();
            order.markPaid();

            // act
            CoreException result = assertThrows(CoreException.class, order::markFailed);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
