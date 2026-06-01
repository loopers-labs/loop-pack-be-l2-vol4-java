package com.loopers.order.interfaces;

import com.loopers.order.application.OrderInfo;
import com.loopers.order.application.OrderItemInfo;

import java.util.List;

public class OrderV1Dto {

    public record OrderItemRequest(Long productId, Integer quantity) {}

    public record CreateRequest(List<OrderItemRequest> items) {}

    public record OrderItemResponse(Long id, Long productId, String productName, Long price, Integer quantity) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                info.id(),
                info.productId(),
                info.productName(),
                info.price(),
                info.quantity()
            );
        }
    }

    public record OrderResponse(Long id, Long userId, String status, List<OrderItemResponse> items) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(info.id(), info.userId(), info.status(), items);
        }
    }
}
