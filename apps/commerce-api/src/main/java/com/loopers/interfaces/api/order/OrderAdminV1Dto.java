package com.loopers.interfaces.api.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    public record OrderSummary(
            Long orderId,
            Long userId,
            String status,
            BigDecimal originalPrice,
            BigDecimal discountAmount,
            BigDecimal totalPrice,
            ZonedDateTime createdAt
    ) {
        public static OrderSummary from(Order order) {
            return new OrderSummary(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getOriginalPrice(),
                order.getDiscountAmount(),
                order.getTotalPrice(),
                order.getCreatedAt()
            );
        }
    }

    public record OrderResponse(
            Long orderId,
            Long userId,
            String status,
            BigDecimal originalPrice,
            BigDecimal discountAmount,
            BigDecimal totalPrice,
            ZonedDateTime createdAt,
            List<OrderItemSummary> items
    ) {
        public static OrderResponse from(Order order, List<OrderItem> items) {
            return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getOriginalPrice(),
                order.getDiscountAmount(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                items.stream().map(OrderItemSummary::from).toList()
            );
        }
    }

    public record OrderItemSummary(
            Long productId,
            String productName,
            BigDecimal productPrice,
            int quantity
    ) {
        public static OrderItemSummary from(OrderItem item) {
            return new OrderItemSummary(
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice(),
                item.getQuantity()
            );
        }
    }
}
