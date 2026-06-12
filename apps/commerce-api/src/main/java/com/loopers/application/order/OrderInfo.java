package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.util.List;

public record OrderInfo(
    Long orderId,
    String status,
    Long originalAmount,
    Long discountAmount,
    Long totalPrice,
    List<OrderItemInfo> items
) {
    public static OrderInfo of(OrderModel order, List<OrderItemModel> items) {
        return new OrderInfo(
            order.getId(),
            order.getStatus().name(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getTotalPrice(),
            items.stream().map(OrderItemInfo::from).toList()
        );
    }

    public record OrderItemInfo(Long productId, String productName, Long price, int quantity) {
        public static OrderItemInfo from(OrderItemModel item) {
            return new OrderItemInfo(item.getProductId(), item.getProductName(), item.getPrice(), item.getQuantity());
        }
    }
}
