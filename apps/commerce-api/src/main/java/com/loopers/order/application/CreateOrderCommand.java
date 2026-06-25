package com.loopers.order.application;

import java.util.List;

public record CreateOrderCommand(
    Long userId,
    List<Item> items,
    Long userCouponId
) {

    public CreateOrderCommand {
        items = List.copyOf(items);
    }

    public CreateOrderCommand(Long userId, List<Item> items) {
        this(userId, items, null);
    }

    public record Item(
        Long productId,
        int quantity
    ) {
    }
}
