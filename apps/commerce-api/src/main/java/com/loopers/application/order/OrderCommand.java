package com.loopers.application.order;

import java.util.List;

public class OrderCommand {

    public record Create(Long userId, List<Item> items) {
        public record Item(Long productId, int quantity) {}
    }
}
