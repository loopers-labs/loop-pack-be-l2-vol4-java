package com.loopers.order.application;

import com.loopers.order.domain.OrderModel;

import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    String status,
    List<OrderItemInfo> items,
    Long originalAmount,
    Long discountAmount,
    Long finalAmount
) {

    public static OrderInfo from(OrderModel model) {
        List<OrderItemInfo> items = model.getItems().stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(
            model.getId(),
            model.getUserId(),
            model.getStatus().name(),
            items,
            model.getOriginalAmount(),
            model.getDiscountAmount(),
            model.getFinalAmount()
        );
    }
}
