package com.loopers.application.ordering.order;

import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderLine;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderResult {
    public record Summary(
        Long orderId,
        String userId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        Long totalAmount,
        ZonedDateTime createdAt
    ) {
        public static Summary from(Order order, Payment payment) {
            return new Summary(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                payment.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
            );
        }
    }

    public record Detail(
        Long orderId,
        String userId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        String failureReason,
        Long totalAmount,
        List<Item> items
    ) {
        public static Detail from(Order order, Payment payment) {
            return new Detail(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                payment.getStatus(),
                payment.getFailureReason(),
                order.getTotalAmount(),
                order.getLines().stream()
                    .map(Item::from)
                    .toList()
            );
        }
    }

    public record Item(
        Long productId,
        String productName,
        Integer quantity,
        Long unitPrice,
        Long lineAmount
    ) {
        private static Item from(OrderLine line) {
            return new Item(
                line.getProductId(),
                line.getProductName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getLineAmount()
            );
        }
    }
}
