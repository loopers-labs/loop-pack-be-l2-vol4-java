package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    Long totalAmount,
    OrderStatus status,
    List<Item> items,
    ZonedDateTime createdAt
) {
    public record Item(
        Long productId,
        Integer quantity,
        String productName,
        Long productPrice,
        String brandName
    ) {
        public static Item from(OrderItem orderItem) {
            return new Item(
                orderItem.getProductId(),
                orderItem.getQuantity(),
                orderItem.getProductName(),
                orderItem.getProductPrice(),
                orderItem.getBrandName()
            );
        }
    }

    public static OrderInfo from(OrderModel order, List<OrderItem> items) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getStatus(),
            items.stream().map(Item::from).toList(),
            order.getCreatedAt()
        );
    }
}
