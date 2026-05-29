package com.loopers.application.order;

import java.util.List;

public record OrderCreateRequest(
    List<Item> items
) {
    public record Item(
        Long productId,
        int quantity
    ) {
    }
}
