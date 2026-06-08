package com.loopers.application.order;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public Map<Long, Integer> orderQuantities() {
        return items.stream()
            .collect(Collectors.toMap(Item::productId, Item::quantity));
    }

    public record Item(
        Long productId,
        int quantity
    ) {
    }
}
