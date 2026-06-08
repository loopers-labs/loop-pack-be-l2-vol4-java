package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

public record OrderItemInfo(
    Long productId,
    String productName,
    Long unitPrice,
    int quantity
) {
    public static OrderItemInfo from(OrderItemModel item) {
        return new OrderItemInfo(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice(),
            item.getQuantity()
        );
    }
}
