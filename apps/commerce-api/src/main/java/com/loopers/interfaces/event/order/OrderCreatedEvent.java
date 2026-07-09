package com.loopers.interfaces.event.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.util.List;

public record OrderCreatedEvent(
    Long orderId,
    Long memberId,
    Long totalPrice,
    List<OrderItemInfo> items
) {
    public static OrderCreatedEvent from(OrderModel order, List<OrderItemModel> orderItems) {
        List<OrderItemInfo> items = orderItems.stream()
            .map(item -> new OrderItemInfo(item.getProductId(), item.getQuantity()))
            .toList();
        return new OrderCreatedEvent(order.getId(), order.getMemberId(), order.getTotalPrice(), items);
    }

    public record OrderItemInfo(Long productId, int quantity) {}
}
