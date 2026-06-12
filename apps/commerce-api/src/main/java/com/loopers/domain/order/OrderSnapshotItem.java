package com.loopers.domain.order;

public record OrderSnapshotItem(
        Long productId,
        String productName,
        Long productPrice,
        Integer quantity,
        Long subtotal
) {}
