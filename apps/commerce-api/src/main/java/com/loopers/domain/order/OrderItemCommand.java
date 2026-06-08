package com.loopers.domain.order;

public record OrderItemCommand(
    Long productId,
    String productName,
    Long unitPrice,
    int quantity
) {}
