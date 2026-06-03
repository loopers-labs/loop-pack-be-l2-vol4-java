package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    String status,
    Long totalAmount,
    String paymentMethod,
    String failureReason,
    List<OrderItemInfo> items
) {
    public static OrderInfo from(OrderModel order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus().name(),
            order.getTotalAmount().getAmount(),
            order.getPaymentMethod().name(),
            order.getFailureReason(),
            order.getItems().stream().map(OrderItemInfo::from).toList()
        );
    }
}
