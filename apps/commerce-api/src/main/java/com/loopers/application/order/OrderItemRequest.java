package com.loopers.application.order;

public record OrderItemRequest(Long productId, int quantity) {
}
