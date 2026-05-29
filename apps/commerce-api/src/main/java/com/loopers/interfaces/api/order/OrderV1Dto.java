package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(List<Item> items) {
        public record Item(Long productId, int quantity) {}
    }

    public record OrderResponse(
        Long orderId,
        Long userId,
        Long totalPrice,
        String status,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(info.orderId(), info.userId(), info.totalPrice(), info.status(), items);
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        Long unitPrice,
        int quantity,
        Long subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.productId(),
                item.productName(),
                item.unitPrice(),
                item.quantity(),
                item.subtotal()
            );
        }
    }
}
