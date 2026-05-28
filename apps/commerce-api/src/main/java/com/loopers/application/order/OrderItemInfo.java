package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

import java.math.BigDecimal;

public record OrderItemInfo(
        Long productId,
        String productName,
        BigDecimal productPrice,
        Long quantity,
        BigDecimal subtotal
) {
    public static OrderItemInfo from(OrderItemModel item) {
        return new OrderItemInfo(
                item.getProductId(),
                item.getProductSnapshot().name(),
                item.getProductSnapshot().price(),
                item.getQuantity(),
                item.subtotal()
        );
    }
}
