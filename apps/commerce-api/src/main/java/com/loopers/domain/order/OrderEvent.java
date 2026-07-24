package com.loopers.domain.order;

import com.loopers.domain.UserActivityEvent;
import com.loopers.domain.outbox.OutboxableEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class OrderEvent {

    public record OrderCreated(String eventId, Long orderId, Long userId, List<OrderCreatedItem> items)
            implements OutboxableEvent, UserActivityEvent {

        public record OrderCreatedItem(Long productId, Long quantity, BigDecimal price) {
        }

        public static OrderCreated from(OrderModel order) {
            List<OrderCreatedItem> items = order.getItems().stream()
                    .map(item -> new OrderCreatedItem(item.getProductId(), item.getQuantity(), item.getProductSnapshot().price()))
                    .toList();
            return new OrderCreated(UUID.randomUUID().toString(), order.getId(), order.getUserId(), items);
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
}
