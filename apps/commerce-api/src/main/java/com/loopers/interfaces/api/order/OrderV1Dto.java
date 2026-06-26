package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderLineCommand;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public class OrderV1Dto {

    public record PlaceOrderRequest(List<OrderLineRequest> items, Long couponId) {
        public List<OrderLineCommand> toCommands() {
            return items.stream()
                .map(line -> new OrderLineCommand(line.productId(), line.quantity()))
                .toList();
        }
    }

    public record OrderLineRequest(Long productId, int quantity) {}

    public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal paymentAmount,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.totalAmount(),
                info.discountAmount(),
                info.paymentAmount(),
                info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(),
                info.productName(),
                info.unitPrice(),
                info.quantity()
            );
        }
    }
}
