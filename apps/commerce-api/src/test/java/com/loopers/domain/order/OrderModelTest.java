package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderModelTest {

    @Test
    @DisplayName("二쇰Ц ?앹꽦 ???곹깭??PENDING?대ŉ ?ㅻ━吏??湲덉븸, ?좎씤 湲덉븸, 理쒖쥌 寃곗젣 湲덉븸 諛?荑좏룿 ?앸퀎?먭? ?ㅼ젙?쒕떎.")
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
    @DisplayName("二쇰Ц ?곹깭媛 PENDING????complete()???몄텧?섎㈃ COMPLETED濡?蹂寃쎈맂??")
    void complete_FromPending_ShouldChangeStatusToCompleted() {
        // given
        OrderModel order = new OrderModel(1L, null, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));

        // when
        order.complete();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("二쇰Ц ?곹깭媛 PENDING????cancel()???몄텧?섎㈃ CANCELED濡?蹂寃쎈맂??")
    void cancel_FromPending_ShouldChangeStatusToCanceled() {
        // given
        OrderModel order = new OrderModel(1L, null, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));

        // when
        order.cancel();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }
}
