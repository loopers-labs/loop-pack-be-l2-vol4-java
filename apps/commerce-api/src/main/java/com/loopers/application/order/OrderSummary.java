package com.loopers.application.order;

import com.loopers.domain.order.model.Order;

import java.time.ZonedDateTime;

public record OrderSummary(Long orderId, Long originalAmount, Long discountAmount, Long totalAmount, ZonedDateTime orderedAt) {

    public static OrderSummary from(Order order) {
        return new OrderSummary(
            order.getId(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getTotalAmount(),
            order.getCreatedAt()
        );
    }
}
