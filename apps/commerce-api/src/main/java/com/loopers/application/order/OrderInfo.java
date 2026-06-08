package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.util.List;

public record OrderInfo(
    Long orderId,
    Long userId,
    Long originalAmount,
    Long discountAmount,
    Long finalAmount,
    Long userCouponId,
    String status,
    List<OrderItemInfo> items
) {
    public static OrderInfo from(Order order) {
        List<OrderItemInfo> items = order.getItems().stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getOriginalAmount().amount().longValue(),
            order.getDiscountAmount().amount().longValue(),
            order.getFinalAmount().amount().longValue(),
            order.getUserCouponId(),
            order.getStatus().name(),
            items
        );
    }

    public record OrderItemInfo(
        Long productId,
        String productName,
        Long unitPrice,
        int quantity,
        Long subtotal
    ) {
        public static OrderItemInfo from(OrderItem item) {
            return new OrderItemInfo(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice().amount().longValue(),
                item.getQuantity().value(),
                item.subtotal().amount().longValue()
            );
        }
    }
}
