package com.loopers.application.order;

import java.time.ZonedDateTime;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

public record OrderAdminSummaryInfo(
    Long orderId,
    Long userId,
    OrderStatus status,
    ZonedDateTime orderedAt,
    Integer totalPrice
) {

    public static OrderAdminSummaryInfo from(OrderModel order) {
        return new OrderAdminSummaryInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getOrderedAt(),
            order.getTotalPrice()
        );
    }
}
