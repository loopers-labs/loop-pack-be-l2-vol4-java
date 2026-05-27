package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

public record OrderItemInfo(
    Long productId,
    String productName,
    String brandName,
    Integer unitPrice,
    Integer quantity
) {

    public static OrderItemInfo from(OrderItemModel orderItem) {
        return new OrderItemInfo(
            orderItem.getProductId(),
            orderItem.getProductName(),
            orderItem.getProductBrandName(),
            orderItem.getUnitPrice(),
            orderItem.getQuantity().value()
        );
    }
}
