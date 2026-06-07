package com.loopers.application.ordering.order;

import java.util.List;

public class OrderCommand {
    public record Create(String userId, List<Item> items, Long couponId) {
        public Create(String userId, List<Item> items) {
            this(userId, items, null);
        }
    }

    public record Item(Long productId, Integer quantity) {}
}
