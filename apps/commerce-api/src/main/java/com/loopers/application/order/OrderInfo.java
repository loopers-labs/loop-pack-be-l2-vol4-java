package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public final class OrderInfo {

    private OrderInfo() {
    }

    public record Item(Long productId, String productName, long unitPrice, int quantity, long subtotal) {

        public static Item from(OrderItem item) {
            return new Item(
                    item.getProductId(),
                    item.getProductName(),
                    item.getUnitPrice().getAmount(),
                    item.getQuantity(),
                    item.subtotal().getAmount()
            );
        }
    }

    public record Created(
            Long id,
            Long userId,
            long totalAmount,
            OrderStatus status,
            List<Item> items,
            LocalDateTime orderedAt
    ) {

        public static Created from(Order order) {
            return new Created(
                    order.getId(),
                    order.getUserId(),
                    order.getTotalAmount().getAmount(),
                    order.getStatus(),
                    order.getItems().stream().map(Item::from).toList(),
                    order.getOrderedAt()
            );
        }
    }

    public record Detail(
            Long id,
            Long userId,
            long totalAmount,
            OrderStatus status,
            List<Item> items,
            LocalDateTime orderedAt
    ) {

        public static Detail from(Order order) {
            return new Detail(
                    order.getId(),
                    order.getUserId(),
                    order.getTotalAmount().getAmount(),
                    order.getStatus(),
                    order.getItems().stream().map(Item::from).toList(),
                    order.getOrderedAt()
            );
        }
    }

    public record ListItem(
            Long id,
            Long userId,
            long totalAmount,
            OrderStatus status,
            int itemCount,
            LocalDateTime orderedAt
    ) {

        public static ListItem from(Order order) {
            return new ListItem(
                    order.getId(),
                    order.getUserId(),
                    order.getTotalAmount().getAmount(),
                    order.getStatus(),
                    order.getItems().size(),
                    order.getOrderedAt()
            );
        }
    }
}
