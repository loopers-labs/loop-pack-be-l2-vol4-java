package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

public record OrderItemInfo(
    Long productId,
    String productName,
    Long price,
    Integer quantity,
    Long subtotal
) {
    public static OrderItemInfo from(OrderItemModel item) {
        return new OrderItemInfo(
            item.getProductId(),
            item.getProductNameSnapshot(),
            item.getPriceSnapshot(),
            item.getQuantity(),
            item.subtotal().getAmount()
        );
    }
}
