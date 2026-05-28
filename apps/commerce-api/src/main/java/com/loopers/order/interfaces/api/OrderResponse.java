package com.loopers.order.interfaces.api;

import com.loopers.order.application.OrderInfo;

import java.util.List;

public record OrderResponse(
    Long id, Long memberId, Long totalAmount, List<OrderItemResponse> items) {
    public static OrderResponse from(OrderInfo info) {
        return new OrderResponse(
            info.id(),
            info.memberId(),
            info.totalAmount(),
            info.items().stream().map(OrderItemResponse::from).toList());
    }
}
