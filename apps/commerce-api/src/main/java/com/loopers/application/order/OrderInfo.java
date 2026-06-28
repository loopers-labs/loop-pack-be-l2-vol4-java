package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public record OrderInfo(Long id, Long userId, OrderStatus status, Long totalAmount, List<OrderItemInfo> items) {

    public record OrderItemInfo(Long productId, String productNameSnapshot, Long productPriceSnapshot, Integer quantity) {
        public static OrderItemInfo from(OrderItem item) {
            return new OrderItemInfo(
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getProductPriceSnapshot(),
                item.getQuantity()
            );
        }
    }

    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getItems().stream().map(OrderItemInfo::from).toList()
        );
    }
}
