package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.util.List;

public record OrderInfo(Long id, Long userId, Long totalPrice, List<OrderItemInfo> items) {

    public record OrderItemInfo(Long productId, String productName, Long productPrice, Integer quantity) {
        public static OrderItemInfo from(OrderItem item) {
            return new OrderItemInfo(item.getProductId(), item.getProductName(), item.getProductPrice(), item.getQuantity());
        }
    }

    public static OrderInfo from(Order order) {
        List<OrderItemInfo> itemInfos = order.getItems().stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(order.getId(), order.getUserId(), order.getTotalPrice(), itemInfos);
    }
}
