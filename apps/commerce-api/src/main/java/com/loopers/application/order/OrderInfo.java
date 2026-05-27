package com.loopers.application.order;

import java.time.ZonedDateTime;
import java.util.List;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

public record OrderInfo(
    Long orderId,
    OrderStatus status,
    ZonedDateTime orderedAt,
    Integer totalPrice,
    List<OrderItemInfo> items
) {

    public static OrderInfo from(OrderModel order, List<OrderItemModel> orderItems) {
        List<OrderItemInfo> orderItemsInfo = orderItems.stream()
            .map(OrderItemInfo::from)
            .toList();

        return new OrderInfo(
            order.getId(),
            order.getStatus(),
            order.getOrderedAt(),
            order.getTotalPrice(),
            orderItemsInfo
        );
    }
}
