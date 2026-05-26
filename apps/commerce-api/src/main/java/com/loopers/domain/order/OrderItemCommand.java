package com.loopers.domain.order;

public record OrderItemCommand(Long productId, int quantity) {}
