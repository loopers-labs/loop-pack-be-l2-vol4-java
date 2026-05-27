package com.loopers.application.ordering.order;

import java.util.List;

public class OrderCommand {
    public record Create(String userId, List<Item> items) {}

    public record Item(Long productId, Integer quantity) {}
}
