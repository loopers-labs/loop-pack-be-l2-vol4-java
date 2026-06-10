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
    Integer originalAmount,
    Integer discountAmount,
    Integer finalAmount,
    Long userCouponId,
    List<OrderItemInfo> items
) {

    public static OrderInfo of(OrderModel order, List<OrderItemModel> orderItems) {
        List<OrderItemInfo> orderItemsInfo = orderItems.stream()
            .map(OrderItemInfo::from)
            .toList();

        return new OrderInfo(
            order.getId(),
            order.getStatus(),
            order.getOrderedAt(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getFinalAmount(),
            order.getUserCouponId(),
            orderItemsInfo
        );
    }
}
