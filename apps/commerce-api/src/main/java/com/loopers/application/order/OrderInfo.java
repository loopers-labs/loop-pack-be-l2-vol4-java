package com.loopers.application.order;

import com.loopers.domain.order.OrderFailure;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public record OrderInfo(
    Long id,
    String userLoginId,
    OrderStatus status,
    Long totalAmount,
    List<OrderLineInfo> orderLines,
    List<OrderFailureInfo> failures
) {
    public static OrderInfo from(OrderResult result) {
        Order order = result.order();
        return new OrderInfo(
            order.getId(),
            order.getUserLoginId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getOrderLines().stream()
                .map(OrderLineInfo::from)
                .toList(),
            result.failures().stream()
                .map(OrderFailureInfo::from)
                .toList()
        );
    }

    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getUserLoginId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getOrderLines().stream()
                .map(OrderLineInfo::from)
                .toList(),
            List.of()
        );
    }

    public record OrderLineInfo(
        Long productId,
        String productName,
        Long price,
        Integer quantity,
        Long amount
    ) {
        public static OrderLineInfo from(OrderLine orderLine) {
            return new OrderLineInfo(
                orderLine.getProductId(),
                orderLine.getProductName(),
                orderLine.getPrice(),
                orderLine.getQuantity(),
                orderLine.getAmount()
            );
        }
    }

    public record OrderFailureInfo(
        Long productId,
        Integer quantity,
        String reason
    ) {
        public static OrderFailureInfo from(OrderFailure failure) {
            return new OrderFailureInfo(
                failure.productId(),
                failure.quantity(),
                failure.reason()
            );
        }
    }
}
