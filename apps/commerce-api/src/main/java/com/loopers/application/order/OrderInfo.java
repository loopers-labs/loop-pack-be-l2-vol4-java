package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public record OrderInfo(
        Long id,
        Long userId,
        OrderStatus status,
        Long totalAmount,
        List<OrderItemInfo> items
) {
    public static OrderInfo from(OrderModel order) {
        List<OrderItemInfo> orderItemInfos = order.getOrderItems().stream()
                                                                  .map(OrderItemInfo::from)
                                                                  .toList();
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            orderItemInfos
        );
    }
}