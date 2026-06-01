package com.loopers.order.application;

public record OrderItemCommand(Long productId, Integer quantity) {
}
