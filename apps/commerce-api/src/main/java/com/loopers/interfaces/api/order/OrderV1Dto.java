package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public class OrderV1Dto {

    public record OrderItemRequest(Long productId, Integer quantity) {}

    public record CreateOrderRequest(List<OrderItemRequest> items) {}

    public record OrderItemResponse(
        Long productId,
        String productNameSnapshot,
        Long productPriceSnapshot,
        Integer quantity
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(),
                info.productNameSnapshot(),
                info.productPriceSnapshot(),
                info.quantity()
            );
        }
    }

    public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        Long totalAmount,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.totalAmount(),
                info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }
}
