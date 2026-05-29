package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long orderId,
    Long userId,
    Long totalPrice,
    String status,
    ZonedDateTime createdAt,
    List<OrderLineInfo> lines
) {
    public record OrderLineInfo(
        Long productId,
        String productName,
        Long productPrice,
        Integer quantity,
        Long totalPrice
    ) {}

    public static OrderInfo from(OrderModel order) {
        List<OrderLineInfo> lines = order.getOrderLines().stream()
            .map(line -> new OrderLineInfo(
                line.getProductId(),
                line.getProductName(),
                line.getProductPrice(),
                line.getQuantity(),
                line.getTotalPrice()
            ))
            .toList();
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getTotalPrice(),
            order.getStatus().name(),
            order.getCreatedAt(),
            lines
        );
    }
}
