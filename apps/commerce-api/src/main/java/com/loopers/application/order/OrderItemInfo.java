package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

public record OrderItemInfo(
    Long productId,
    String productName,
    String brandName,
    String imageUrl,
    Long unitPrice,
    Integer quantity,
    Long lineTotal
) {
    public static OrderItemInfo from(OrderItem item) {
        return new OrderItemInfo(
            item.getProductId(),
            item.getProductName(),
            item.getBrandName(),
            item.getImageUrl(),
            item.getUnitPrice().getAmount(),
            item.getQuantity(),
            item.getLineTotal().getAmount()
        );
    }
}
