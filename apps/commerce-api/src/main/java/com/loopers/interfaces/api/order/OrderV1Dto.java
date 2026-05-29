package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.PlaceOrderCriteria;

import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
        List<OrderItemRequest> items
    ) {
        public PlaceOrderCriteria toCriteria() {
            if (items == null) {
                return new PlaceOrderCriteria(List.of());
            }
            return new PlaceOrderCriteria(
                items.stream()
                    .map(item -> new PlaceOrderCriteria.Item(item.productId(), item.quantity()))
                    .toList()
            );
        }
    }

    public record OrderItemRequest(
        Long productId,
        int quantity
    ) {}

    public record OrderResponse(
        Long id,
        Long userId,
        String status,
        Long totalAmount,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.totalAmount(),
                info.items().stream()
                    .map(OrderItemResponse::from)
                    .toList()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        Long price,
        Integer quantity,
        Long subtotal
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(),
                info.productName(),
                info.price(),
                info.quantity(),
                info.subtotal()
            );
        }
    }
}
