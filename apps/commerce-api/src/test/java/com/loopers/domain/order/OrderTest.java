package com.loopers.domain.order;

import com.loopers.domain.order.vo.OrderPayment;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    @DisplayName("쿠폰 할인 결과가 주어지면, 주문 금액 스냅샷을 저장한다.")
    @Test
    void createsOrderWithCouponPaymentSnapshot_whenCouponDiscountIsProvided() {
        // arrange
        Long userId = 1L;
        Long userCouponId = 10L;
        OrderItem iphone = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);
        OrderPayment payment = OrderPayment.withDiscount(1_550_000L, 2_000L);

        // act
        Order order = Order.create(userId, OrderItems.of(List.of(iphone)), userCouponId, payment);

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
        OrderPayment payment = OrderPayment.withDiscount(1_550_000L, 1_550_000L);

        // act
        Order order = Order.create(1L, OrderItems.of(List.of(item)), 10L, payment);

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
            () -> assertThat(order.isOrderedBy(userId)).isTrue(),
            () -> assertThat(order.isOrderedBy(2L)).isFalse()
        );
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
}
