package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(List<OrderItemRequest> items) {}

    public record OrderItemRequest(Long productId, int quantity) {}

    public record OrderResponse(
        Long id,
        Long userId,
        String status,
        Long totalPrice,
        ZonedDateTime orderedAt,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> itemResponses = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.totalPrice(),
                info.orderedAt(),
                itemResponses
            );
        }
    }

    public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        Long productPrice,
        int quantity,
        Long subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.id(),
                item.productId(),
                item.productName(),
                item.productPrice(),
                item.quantity(),
                item.subtotal()
            );
        }
    }
}
