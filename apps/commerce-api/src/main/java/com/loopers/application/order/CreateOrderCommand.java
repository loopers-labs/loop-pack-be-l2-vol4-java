package com.loopers.application.order;

import java.util.List;

public record CreateOrderCommand(
    Long userId,
    List<Item> items
) {

    public CreateOrderCommand {
        items = List.copyOf(items);
    }

    public List<Long> productIds() {
        return items.stream()
            .map(Item::productId)
            .toList();
    }

    public record Item(
        Long productId,
        int quantity
    ) {
    }
}
