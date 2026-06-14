package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    BigDecimal totalAmount,
    BigDecimal discountAmount,
    BigDecimal paymentAmount,
    List<OrderItemInfo> items
) {
    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount().getAmount(),
            order.getDiscountAmount().getAmount(),
            order.getPaymentAmount().getAmount(),
            order.getItems().stream().map(OrderItemInfo::from).toList()
        );
    }

    public record OrderItemInfo(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity
    ) {
        public static OrderItemInfo from(OrderItem item) {
            return new OrderItemInfo(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice().getAmount(),
                item.getQuantity().getValue()
            );
        }
    }
}
