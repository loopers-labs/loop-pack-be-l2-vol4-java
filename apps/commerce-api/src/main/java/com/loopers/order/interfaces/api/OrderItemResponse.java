package com.loopers.order.interfaces.api;

import com.loopers.order.application.OrderInfo;

public record OrderItemResponse(
    Long productId,
    String productName,
    String brandName,
    Long unitPrice,
    int quantity,
    Long lineAmount) {
    public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
        return new OrderItemResponse(
            info.productId(),
            info.productName(),
            info.brandName(),
            info.unitPrice(),
            info.quantity(),
            info.lineAmount());
    }
}
