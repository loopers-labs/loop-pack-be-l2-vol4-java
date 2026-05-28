package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long id,
        String status,
        BigDecimal totalPrice,
        List<OrderItemInfo> items,
        ZonedDateTime createdAt
) {
    public static OrderInfo from(OrderModel order) {
        return new OrderInfo(
                order.getId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                order.getItems().stream()
                        .map(OrderItemInfo::from)
                        .toList(),
                order.getCreatedAt()
        );
    }
}
