package com.loopers.domain.dataplatform;

import com.loopers.domain.order.event.OrderPlaced;

import java.util.List;

public record DataPlatformPayload(Long orderId, Long userId, long finalAmount, List<Item> items) {

    public record Item(Long productId, int quantity) {}

    public static DataPlatformPayload from(OrderPlaced e) {
        List<Item> items = e.lines().stream()
            .map(l -> new Item(l.productId(), l.quantity()))
            .toList();
        return new DataPlatformPayload(e.orderId(), e.userId(), e.finalAmount(), items);
    }
}
