package com.loopers.application.order;

import java.util.List;

public record OrderCreateRequest(
    List<Item> items,
    Long couponIssueId
) {
    public OrderCreateRequest(List<Item> items) {
        this(items, null);
    }

    public record Item(
        Long productId,
        int quantity
    ) {
    }
}
