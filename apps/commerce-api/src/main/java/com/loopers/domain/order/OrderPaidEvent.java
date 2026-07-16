package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record OrderPaidEvent(
        String eventId,
        Long orderId,
        Long userId,
        Long finalAmount,
        List<Item> items,
        ZonedDateTime occurredAt
) {
    public record Item(Long productId, Integer quantity, Long subtotal) {}

    public static OrderPaidEvent from(OrderModel order) {
        List<Item> items = order.getOrderItems().stream()
                .map(item -> new Item(item.getProductId(), item.getQuantity(), item.subtotal()))
                .toList();
        return new OrderPaidEvent(
                UUID.randomUUID().toString(),
                order.getId(),
                order.getUserId(),
                order.getFinalAmount(),
                items,
                ZonedDateTime.now()
        );
    }
}