package com.loopers.domain.order;

public record OrderItemCommand(Long productId, String productName, Long price, Integer quantity) {}
