package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    Long issuedCouponId,
    Long totalAmount,
    Long discountAmount,
    Long finalAmount,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {
    public static OrderInfo from(OrderModel order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getIssuedCouponId(),
            order.getTotalAmount().value(),
            order.getDiscountAmount().value(),
            order.getFinalAmount().value(),
            order.getItems().stream().map(OrderItemInfo::from).toList(),
            order.getCreatedAt()
        );
    }
}
