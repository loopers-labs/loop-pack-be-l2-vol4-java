package com.loopers.application.order;

public record OrderItemCommand(Long productId, Integer quantity) {
}
