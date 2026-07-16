package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPlacedMessageTest {

    @DisplayName("주문 라인은 상품 가격 × 수량을 라인 금액(lineAmount)으로 싣는다.")
    @Test
    void carriesLineAmountAsPriceTimesQuantity() {
        // given
        OrderItem item = OrderItem.of(1L, 101L, 3, "청바지", 12_000L, "리바이스");

        // when
        OrderPlacedMessage message = OrderPlacedMessage.of(1L, List.of(item));

        // then
        assertThat(message.lines()).hasSize(1);
        assertThat(message.lines().get(0).lineAmount()).isEqualTo(36_000L);
    }

    @DisplayName("여러 주문 라인은 각자의 가격 × 수량으로 라인 금액을 계산한다.")
    @Test
    void computesLineAmountPerLineIndependently() {
        // given
        OrderItem cheap = OrderItem.of(1L, 101L, 2, "양말", 1_500L, "나이키");
        OrderItem pricey = OrderItem.of(1L, 202L, 1, "코트", 89_000L, "버버리");

        // when
        OrderPlacedMessage message = OrderPlacedMessage.of(1L, List.of(cheap, pricey));

        // then
        assertThat(message.lines()).extracting(OrderPlacedMessage.Line::lineAmount)
            .containsExactly(3_000L, 89_000L);
    }
}
