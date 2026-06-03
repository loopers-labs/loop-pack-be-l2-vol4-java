package com.loopers.application.order;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderItemEntity;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long orderId,
        Long userId,
        OrderStatus status,
        Long totalAmount,
        List<OrderItemInfo> items,
        ZonedDateTime createdAt
) {
    public record OrderItemInfo(
            Long productId,
            String productName,
            Long productPrice,
            Integer quantity,
            Long subtotal
    ) {
        public static OrderItemInfo from(OrderItemEntity item) {
            return new OrderItemInfo(
                    item.getProductId(),
                    item.getProductName(),
                    item.getProductPrice(),
                    item.getQuantity(),
                    item.subtotal()
            );
        }
    }

    public static OrderInfo from(OrderEntity order) {
        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.calculateTotalAmount(),
                order.getItems().stream().map(OrderItemInfo::from).toList(),
                order.getCreatedAt()
        );
    }
}
