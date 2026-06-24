package com.loopers.order.domain;

import com.loopers.order.domain.vo.OrderAmountSnapshot;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    @DisplayName("쿠폰 할인 결과가 주어지면, 주문 금액 스냅샷을 저장한다.")
    @Test
    void createsOrderWithAmountSnapshot_whenCouponDiscountIsProvided() {
        // arrange
        Long userId = 1L;
        Long userCouponId = 10L;
        OrderItem iphone = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);
        OrderAmountSnapshot amountSnapshot = OrderAmountSnapshot.withDiscount(1_550_000L, 2_000L);

        // act
        Order order = Order.create(userId, OrderItems.of(List.of(iphone)), userCouponId, amountSnapshot);

        // assert
        assertAll(
            () -> assertThat(order.getAppliedUserCouponId()).isEqualTo(userCouponId),
            () -> assertThat(order.getOrderTotalPrice()).isEqualTo(1_550_000L),
            () -> assertThat(order.getDiscountAmount()).isEqualTo(2_000L),
            () -> assertThat(order.getPaymentAmount()).isEqualTo(1_548_000L)
        );
    }

    @DisplayName("할인 금액이 주문 금액과 같으면, 결제 금액은 0원이다.")
    @Test
    void paymentIsZero_whenDiscountEqualsOrderAmount() {
        // arrange
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);
        OrderAmountSnapshot amountSnapshot = OrderAmountSnapshot.withDiscount(1_550_000L, 1_550_000L);

        // act
        Order order = Order.create(1L, OrderItems.of(List.of(item)), 10L, amountSnapshot);

        // assert
        assertThat(order.getPaymentAmount()).isZero();
    }

    @DisplayName("사용자 ID와 주문 항목들이 주어지면, 주문을 생성하고 전체 금액을 계산한다.")
    @Test
    void createsOrder_whenUserIdAndItemsAreProvided() {
        // arrange
        Long userId = 1L;
        OrderItem iphone = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 2);
        OrderItem iphoneMax = OrderItem.create(1L, "애플", 2L, "아이폰 16 Pro Max", 1_900_000L, 1);

        // act
        Order order = Order.create(userId, OrderItems.of(List.of(iphone, iphoneMax)));

        // assert
        assertAll(
            () -> assertThat(order.getUserId()).isEqualTo(userId),
            () -> assertThat(order.getItems()).containsExactly(iphone, iphoneMax),
            () -> assertThat(order.getOrderTotalPrice()).isEqualTo(5_000_000L),
            () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING),
            () -> assertThat(order.isPayable()).isTrue(),
            () -> assertThat(order.isOrderedBy(userId)).isTrue(),
            () -> assertThat(order.isOrderedBy(2L)).isFalse()
        );
    }

    @DisplayName("결제 성공을 반영하면, 주문은 결제 완료 상태가 된다.")
    @Test
    void completesPayment_whenPaymentSucceeds() {
        // arrange
        Order order = createOrder();

        // act
        order.completePayment();

        // assert
        assertAll(
            () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
            () -> assertThat(order.isPayable()).isFalse()
        );
    }

    @DisplayName("결제 실패를 반영하면, 주문은 결제 실패 상태가 된다.")
    @Test
    void failsPayment_whenPaymentFails() {
        // arrange
        Order order = createOrder();

        // act
        order.failPayment();

        // assert
        assertAll(
            () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
            () -> assertThat(order.isPayable()).isFalse()
        );
    }

    @DisplayName("이미 결제 완료된 주문에 결제 실패를 반영하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenPaidOrderIsMarkedPaymentFailed() {
        // arrange
        Order order = createOrder();
        order.completePayment();

        // act & assert
        assertThatThrownBy(order::failPayment)
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("결제할 수 없는 주문의 결제를 검증하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenOrderIsNotPayable() {
        // arrange
        Order order = createOrder();
        order.completePayment();

        // act & assert
        assertThatThrownBy(order::validatePayable)
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("결제 대기 주문의 결제를 검증하면, 예외를 던지지 않는다.")
    @Test
    void doesNotThrow_whenOrderIsPayable() {
        // arrange
        Order order = createOrder();

        // act & assert
        assertThatCode(order::validatePayable).doesNotThrowAnyException();
    }

    @DisplayName("사용자 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUserIdIsNull() {
        // arrange
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);

        // act & assert
        assertThatThrownBy(() -> Order.create(null, OrderItems.of(List.of(item))))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    private Order createOrder() {
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);
        return Order.create(1L, OrderItems.of(List.of(item)));
    }
}
