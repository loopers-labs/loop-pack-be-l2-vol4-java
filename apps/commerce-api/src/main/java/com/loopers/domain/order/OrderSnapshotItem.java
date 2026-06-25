package com.loopers.domain.order;

public record OrderSnapshotItem(
        String productId,
        String productName,
        Long productPrice,
        Integer quantity,
        Long subtotal
) {}
