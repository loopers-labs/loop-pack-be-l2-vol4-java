package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long orderId,
    OrderStatus status,
    Long totalPrice,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {
    public static OrderInfo of(OrderModel order, List<OrderItemInfo> items) {
        return new OrderInfo(
            order.getId(),
            order.getStatus(),
            order.getTotalPrice(),
            items,
            order.getCreatedAt()
        );
    }
}
