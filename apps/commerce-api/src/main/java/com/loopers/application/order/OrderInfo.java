package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderInfo {

    public record Create(Long orderId) {
        public static Create from(Order order) {
            return new Create(order.getId());
        }
    }

    public record Detail(
            Long orderId,
            String status,
            BigDecimal originalPrice,
            BigDecimal discountAmount,
            BigDecimal totalPrice,
            ZonedDateTime createdAt,
            List<Item> items
    ) {
        public record Item(Long productId, String productName, BigDecimal productPrice, int quantity) {
            public static Item from(OrderItem item) {
                return new Item(item.getProductId(), item.getProductName(), item.getProductPrice(), item.getQuantity());
            }
        }

        public static Detail from(Order order, List<OrderItem> items) {
            return new Detail(
                order.getId(),
                order.getStatus().name(),
                order.getOriginalPrice(),
                order.getDiscountAmount(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                items.stream().map(Item::from).toList()
            );
        }
    }
}
