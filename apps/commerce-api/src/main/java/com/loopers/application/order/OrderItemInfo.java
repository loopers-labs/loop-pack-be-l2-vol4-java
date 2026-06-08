package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

public record OrderItemInfo(
    Long productId,
    String productName,
    int unitPrice,
    String brandName,
    int quantity,
    int totalPrice
) {
    public static OrderItemInfo from(OrderItemModel item) {
        return new OrderItemInfo(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice(),
            item.getBrandName(),
            item.getQuantity(),
            item.totalPrice()
        );
    }
}
