package com.loopers.interfaces.event.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCreatedEventTest {

    @DisplayName("from()은 각 OrderItemModel의 price를 OrderItemInfo에 포함한다.")
    @Test
    void from_includesPricePerItem() {
        OrderModel order = new OrderModel(1L, null, 30000L, 0L);
        List<OrderItemModel> items = List.of(
            new OrderItemModel(order.getId(), 10L, "상품A", 10000L, 2),
            new OrderItemModel(order.getId(), 20L, "상품B", 5000L, 2)
        );

        OrderCreatedEvent event = OrderCreatedEvent.from(order, items);

        assertThat(event.items()).extracting(
            OrderCreatedEvent.OrderItemInfo::productId,
            OrderCreatedEvent.OrderItemInfo::quantity,
            OrderCreatedEvent.OrderItemInfo::price
        ).containsExactly(
            org.assertj.core.groups.Tuple.tuple(10L, 2, 10000L),
            org.assertj.core.groups.Tuple.tuple(20L, 2, 5000L)
        );
    }
}
