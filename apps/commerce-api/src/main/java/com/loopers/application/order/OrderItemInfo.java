package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

public record OrderItemInfo(
    Long productId,
    String productName,
    Long unitPrice,
    Integer quantity,
    Long subtotal
) {
    public static OrderItemInfo from(OrderItem item) {
        return new OrderItemInfo(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice().value(),
            item.getQuantity(),
            item.subtotal().value()
        );
    }
}
