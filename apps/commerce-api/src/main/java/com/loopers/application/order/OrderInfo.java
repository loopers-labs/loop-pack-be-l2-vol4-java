package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    long orderTotalPrice,
    List<Item> items
) {

    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getOrderTotalPrice(),
            order.getItems().stream()
                .map(Item::from)
                .toList()
        );
    }

    public record Item(
        Long brandId,
        String brandName,
        Long productId,
        String productName,
        long unitPrice,
        int quantity,
        long totalPrice
    ) {

        private static Item from(OrderItem orderItem) {
            return new Item(
                orderItem.getBrandId(),
                orderItem.getBrandName(),
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getTotalPrice()
            );
        }
    }
}
