package com.loopers.order.application;

import com.loopers.order.domain.OrderItemModel;

public record OrderItemInfo(Long id, Long productId, String productName, Long price, Integer quantity) {

    public static OrderItemInfo from(OrderItemModel model) {
        return new OrderItemInfo(
            model.getId(),
            model.getProductId(),
            model.getProductName(),
            model.getPrice(),
            model.getQuantity()
        );
    }
}
