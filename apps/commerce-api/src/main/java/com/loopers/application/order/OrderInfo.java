package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    String status,
    Long originalAmount,
    Long discountAmount,
    Long totalPrice,
    ZonedDateTime orderedAt,
    List<OrderItemInfo> items
) {

    public record OrderItemInfo(
        Long id,
        Long productId,
        String productName,
        Long productPrice,
        int quantity,
        Long subtotal
    ) {}

    public static OrderInfo from(OrderModel order) {
        List<OrderItemInfo> itemInfos = order.getItems().stream()
            .map(item -> new OrderItemInfo(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice(),
                item.getQuantity(),
                item.calculateSubtotal()
            ))
            .toList();

        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus().name(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getTotalPrice(),
            order.getOrderedAt(),
            itemInfos
        );
    }
}
