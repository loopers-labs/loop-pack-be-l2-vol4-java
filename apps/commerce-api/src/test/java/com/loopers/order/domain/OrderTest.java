package com.loopers.order.domain;

import com.loopers.common.domain.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    private static final Long USER_ID = 1L;
    private static final String ORDER_NUMBER = "20260528-000001";

    private ShippingDestination shipping() {
        return ShippingDestination.create("김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동");
    }

    private List<OrderItem> items() {
        return List.of(
                OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 2),
                OrderItem.create(20L, "바지", 1L, "루퍼스", 15_000L, 1)
        );
    }

    @Test
    @DisplayName("create 로 생성하면 주문 정보가 저장되고 상태는 PENDING 이다")
    void givenValidInputs_whenCreate_thenStoresFieldsWithPendingStatus() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());

        assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(order.getOrderNumber()).isEqualTo(ORDER_NUMBER),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT),
                () -> assertThat(order.getShippingDestination().getRecipientName()).isEqualTo("김루퍼"),
                () -> assertThat(order.getOrderedAt()).isNotNull()
        );
    }

    @Test
    @DisplayName("총액은 주문 항목 소계의 합으로 산정된다")
    void givenItems_whenCreate_thenTotalAmountIsSumOfSubtotals() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());

        assertThat(order.getTotalAmount().value()).isEqualTo(29_000L * 2 + 15_000L);
    }

    @Test
    @DisplayName("userId 가 null 이면 CoreException 이 발생한다")
    void givenNullUserId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Order.create(null, ORDER_NUMBER, shipping(), items()))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("userId 는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("주문번호가 비어 있으면 CoreException 이 발생한다")
    void givenBlankOrderNumber_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> Order.create(USER_ID, invalid, shipping(), items()))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("주문번호는 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("배송지 정보가 null 이면 CoreException 이 발생한다")
    void givenNullShipping_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Order.create(USER_ID, ORDER_NUMBER, null, items()))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("배송지 정보는 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("주문 항목이 비어 있으면 CoreException 이 발생한다")
    void givenEmptyItems_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Order.create(USER_ID, ORDER_NUMBER, shipping(), List.of()))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("주문 항목은 하나 이상이어야 합니다.");
    }

    @Test
    @DisplayName("쿠폰 미적용 시 할인은 0, 최종 결제 금액은 총액과 같고 적용 쿠폰은 없다")
    void givenNoCoupon_whenCreate_thenFinalEqualsTotalWithNoDiscount() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());

        assertAll(
                () -> assertThat(order.getDiscountAmount().value()).isEqualTo(0L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(order.getTotalAmount()),
                () -> assertThat(order.getUserCouponId()).isNull()
        );
    }

    @Test
    @DisplayName("할인을 적용하면 적용 쿠폰·할인액을 저장하고 최종 결제 금액은 총액에서 차감된다")
    void givenDiscount_whenApplyDiscount_thenStoresDiscountAndReducesFinal() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());
        long total = order.getTotalAmount().value(); // 29,000*2 + 15,000 = 73,000

        order.applyDiscount(50L, Money.of(3_000L));

        assertAll(
                () -> assertThat(order.getUserCouponId()).isEqualTo(50L),
                () -> assertThat(order.getDiscountAmount().value()).isEqualTo(3_000L),
                () -> assertThat(order.getFinalAmount().value()).isEqualTo(total - 3_000L)
        );
    }

    @Test
    @DisplayName("할인액이 총액보다 크면 총액으로 클램핑되고 최종 결제 금액은 0 이다")
    void givenDiscountOverTotal_whenApplyDiscount_thenClampsToZero() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());
        long total = order.getTotalAmount().value();

        order.applyDiscount(50L, Money.of(total + 10_000L));

        assertAll(
                () -> assertThat(order.getDiscountAmount().value()).isEqualTo(total),
                () -> assertThat(order.getFinalAmount().value()).isEqualTo(0L)
        );
    }

    @Test
    @DisplayName("PENDING_PAYMENT 에서 markPaid 하면 PAID 로 전이한다")
    void givenPendingPaymentOrder_whenMarkPaid_thenStatusIsPaid() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PENDING_PAYMENT 에서 markPaymentFailed 하면 PAYMENT_FAILED 로 전이한다")
    void givenPendingPaymentOrder_whenMarkPaymentFailed_thenStatusIsPaymentFailed() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());

        order.markPaymentFailed();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("이미 terminal 인 주문은 다시 전이하지 않는다 (멱등 가드)")
    void givenTerminalOrder_whenMarkPaidAgain_thenIgnored() {
        Order order = Order.create(USER_ID, ORDER_NUMBER, shipping(), items());
        order.markPaymentFailed();

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }
}
