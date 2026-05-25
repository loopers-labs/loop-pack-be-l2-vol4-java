package com.loopers.application.order;

public record OrderLineCommand(Long productId, Integer quantity) {
}
