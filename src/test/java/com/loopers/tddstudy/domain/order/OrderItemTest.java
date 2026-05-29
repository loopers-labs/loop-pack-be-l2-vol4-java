package com.loopers.tddstudy.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderItemTest {

    @Test
    @DisplayName("주문 항목을 생성할 수 있다")
    void create_order_item_success() {
        OrderItem item = new OrderItem(1L, "나이키 운동화", 50000, 2);

        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getProductNameSnapshot()).isEqualTo("나이키 운동화");
        assertThat(item.getPriceSnapshot()).isEqualTo(50000);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("항목 금액은 단가 * 수량이다")
    void line_amount_equals_price_times_quantity() {
        OrderItem item = new OrderItem(1L, "나이키 운동화", 50000, 2);

        assertThat(item.lineAmount()).isEqualTo(100000);
    }

    @Test
    @DisplayName("수량이 1 미만이면 예외가 발생한다")
    void create_order_item_zero_quantity_throws_exception() {
        assertThatThrownBy(() -> new OrderItem(1L, "나이키 운동화", 50000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수량은 1 이상이어야 합니다.");
    }
}
