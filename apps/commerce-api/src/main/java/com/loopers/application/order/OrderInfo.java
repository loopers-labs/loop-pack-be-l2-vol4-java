package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;

public record OrderInfo(
        Long id,
        Long memberId,
        OrderStatus status,
        Long originalAmount,
        Long discountAmount,
        Long totalAmount,
        Long couponId,
        ZonedDateTime createdAt
) {
    public static OrderInfo from(OrderModel order) {
        return new OrderInfo(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCouponId(),
                order.getCreatedAt()
        );
    }
}
