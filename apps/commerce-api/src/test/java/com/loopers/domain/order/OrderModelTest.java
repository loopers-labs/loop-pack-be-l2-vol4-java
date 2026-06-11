package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderModelTest {

    @Test
    @DisplayName("주문 생성 시 상태는 PENDING이며 오리지널 금액, 할인 금액, 최종 결제 금액 및 쿠폰 식별자가 설정된다.")
    void constructor_ShouldInitializeFieldsAndStatusAsPending() {
        // given
        Long userId = 1L;
        Long couponIssueId = 42L;
        BigDecimal totalOriginalAmount = new BigDecimal("10000");
        BigDecimal totalDiscountAmount = new BigDecimal("1000");
        BigDecimal totalPaymentAmount = new BigDecimal("9000");

        // when
        OrderModel order = new OrderModel(userId, couponIssueId, totalOriginalAmount, totalDiscountAmount, totalPaymentAmount);

        // then
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getCouponIssueId()).isEqualTo(couponIssueId);
        assertThat(order.getTotalOriginalAmount()).isEqualTo(totalOriginalAmount);
        assertThat(order.getTotalDiscountAmount()).isEqualTo(totalDiscountAmount);
        assertThat(order.getTotalPaymentAmount()).isEqualTo(totalPaymentAmount);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 상태가 PENDING일 때 complete()을 호출하면 COMPLETED로 변경된다.")
    void complete_FromPending_ShouldChangeStatusToCompleted() {
        // given
        OrderModel order = new OrderModel(1L, null, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));

        // when
        order.complete();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("주문 상태가 PENDING일 때 cancel()을 호출하면 CANCELED로 변경된다.")
    void cancel_FromPending_ShouldChangeStatusToCanceled() {
        // given
        OrderModel order = new OrderModel(1L, null, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));

        // when
        order.cancel();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }
}
