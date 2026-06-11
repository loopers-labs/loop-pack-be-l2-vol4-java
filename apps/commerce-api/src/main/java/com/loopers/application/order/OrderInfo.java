package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public record OrderInfo(
        Long id,
        Long userId,
        OrderStatus status,
        Long totalAmount,
        Long couponId,
        Long discountAmount,
        Long finalAmount,
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
            order.getCouponId(),
            order.getDiscountAmount(),
            order.getFinalAmount(),
            orderItemInfos
        );
    }
}