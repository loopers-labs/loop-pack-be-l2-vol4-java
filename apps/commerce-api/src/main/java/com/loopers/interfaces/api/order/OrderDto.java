package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade.OrderRequest;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public class OrderDto {

    public record CreateOrderRequest(List<OrderItemRequest> items) {
        public record OrderItemRequest(Long productId, int quantity) {}

        public List<OrderRequest> toOrderRequests() {
            return items.stream()
                .map(item -> new OrderRequest(item.productId(), item.quantity()))
                .toList();
        }
    }

    public record OrderResponse(
        Long orderId,
        Long userId,
        OrderStatus status,
        List<OrderItemResponse> items
    ) {
        public record OrderItemResponse(
            Long orderItemId,
            Long productId,
            String productName,
            String brandName,
            Long price,
            int quantity
        ) {}

        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.orderId(),
                info.userId(),
                info.status(),
                info.items().stream()
                    .map(item -> new OrderItemResponse(
                        item.orderItemId(),
                        item.productId(),
                        item.productName(),
                        item.brandName(),
                        item.price(),
                        item.quantity()
                    ))
                    .toList()
            );
        }
    }
}
