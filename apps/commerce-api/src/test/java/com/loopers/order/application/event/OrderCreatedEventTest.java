package com.loopers.order.application.event;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.ShippingDestination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OrderCreatedEventTest {

    @Test
    @DisplayName("주문 라인이 이벤트 items(productId, quantity)로 담긴다")
    void givenOrderWithItems_whenOf_thenCarriesLines() {
        List<OrderItem> items = List.of(
                OrderItem.create(100L, "셔츠", 1L, "브랜드", 5_000L, 3),
                OrderItem.create(200L, "바지", 1L, "브랜드", 7_000L, 1)
        );
        Order order = Order.create(1L, "ORD-1",
                ShippingDestination.create("홍길동", "01012345678", "12345", "서울", "101호"), items);

        OrderCreatedEvent event = OrderCreatedEvent.of(order, items);

        assertThat(event.items())
                .extracting(OrderCreatedEvent.Line::productId, OrderCreatedEvent.Line::quantity)
                .containsExactly(tuple(100L, 3), tuple(200L, 1));
    }
}
