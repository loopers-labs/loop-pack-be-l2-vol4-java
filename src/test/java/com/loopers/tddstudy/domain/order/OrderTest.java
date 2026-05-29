package com.loopers.tddstudy.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("주문 생성 시 기본 상태는 PENDING 이다")
    void create_order_default_status_is_pending() {
        Order order = new Order(1L);

        assertThat(order.getUserId()).isEqualTo(1L);
        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getTotalAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("주문 항목을 추가하면 총금액이 계산된다")
    void add_item_calculates_total_amount() {
        Order order = new Order(1L);

        order.addItem(new OrderItem(1L, "나이키 운동화", 50000, 2));
        order.addItem(new OrderItem(2L, "나이키 티셔츠", 30000, 1));

        assertThat(order.getTotalAmount()).isEqualTo(130000);
    }

    @Test
    @DisplayName("결제 성공 시 상태가 PAID 로 변경된다")
    void mark_paid_changes_status_to_paid() {
        Order order = new Order(1L);

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("결제 실패 시 상태가 FAILED 로 변경된다")
    void mark_failed_changes_status_to_failed() {
        Order order = new Order(1L);

        order.markFailed();

        assertThat(order.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("유저 ID 가 null 이면 예외가 발생한다")
    void create_order_null_user_id_throws_exception() {
        assertThatThrownBy(() -> new Order(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유저 ID는 필수입니다.");
    }
}
