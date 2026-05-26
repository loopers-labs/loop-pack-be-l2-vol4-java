package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.domain.order.PaymentMethod;

import java.util.List;

public class OrderV1Dto {

    public record PlaceOrderRequest(
        PaymentMethod paymentMethod,
        List<OrderLineRequest> items
    ) {}

    public record OrderLineRequest(
        Long productId,
        int quantity
    ) {}

    public record OrderResponse(
        Long id,
        Long userId,
        String status,
        Long totalAmount,
        String paymentMethod,
        String failureReason,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.totalAmount(),
                info.paymentMethod(),
                info.failureReason(),
                info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        String brandName,
        String imageUrl,
        Long unitPrice,
        Integer quantity,
        Long lineTotal
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(),
                info.productName(),
                info.brandName(),
                info.imageUrl(),
                info.unitPrice(),
                info.quantity(),
                info.lineTotal()
            );
        }
    }
}
