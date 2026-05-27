package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    int totalAmount,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {
    public static OrderInfo from(OrderModel order) {
        List<OrderItemInfo> itemInfos = order.getItems().stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            itemInfos,
            order.getCreatedAt()
        );
    }
}
