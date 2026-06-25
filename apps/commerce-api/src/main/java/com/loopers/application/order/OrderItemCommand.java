package com.loopers.application.order;

public record OrderItemCommand(String productId, Integer quantity) {
}
