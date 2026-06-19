package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(List<OrderItemRequest> items, Long userCouponId) {}

    public record OrderItemRequest(Long productId, Integer quantity) {}

    public record OrderResponse(
        Long orderId,
        String status,
        Long originalPrice,
        Long discountAmount,
        Long finalPrice,
        List<OrderItemResponse> items,
        ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.orderId(),
                info.status().name(),
                info.originalPrice(),
                info.discountAmount(),
                info.finalPrice(),
                info.items().stream().map(OrderItemResponse::from).toList(),
                info.createdAt()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        Long unitPrice,
        Integer quantity
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(),
                info.productName(),
                info.unitPrice(),
                info.quantity()
            );
        }
    }
}
