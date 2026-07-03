package com.loopers.domain.order;

import com.loopers.domain.DomainEvent;

import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(String eventId, Long orderId, Long userId, List<OrderCreatedItem> items) implements DomainEvent {

    public record OrderCreatedItem(Long productId, Long quantity) {
    }

    public static OrderCreatedEvent from(OrderModel order) {
        List<OrderCreatedItem> items = order.getItems().stream()
                .map(item -> new OrderCreatedItem(item.getProductId(), item.getQuantity()))
                .toList();
        return new OrderCreatedEvent(UUID.randomUUID().toString(), order.getId(), order.getUserId(), items);
    }

    @Override
    public String aggregateType() {
        return "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }

    @Override
    public String eventType() {
        return "ORDER_CREATED";
    }

    public List<Long> productIds() {
        return items.stream().map(OrderCreatedItem::productId).toList();
    }
}
