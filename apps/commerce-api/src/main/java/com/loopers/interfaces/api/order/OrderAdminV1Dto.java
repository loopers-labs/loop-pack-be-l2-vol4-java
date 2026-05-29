package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    public record OrderLineResponse(
        Long productId,
        String productName,
        Long productPrice,
        Integer quantity,
        Long totalPrice
    ) {
        public static OrderLineResponse from(OrderInfo.OrderLineInfo line) {
            return new OrderLineResponse(
                line.productId(),
                line.productName(),
                line.productPrice(),
                line.quantity(),
                line.totalPrice()
            );
        }
    }

    public record OrderResponse(
        Long orderId,
        Long userId,
        Long totalPrice,
        String status,
        ZonedDateTime createdAt,
        List<OrderLineResponse> lines
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.orderId(),
                info.userId(),
                info.totalPrice(),
                info.status(),
                info.createdAt(),
                info.lines().stream().map(OrderLineResponse::from).toList()
            );
        }
    }
}
