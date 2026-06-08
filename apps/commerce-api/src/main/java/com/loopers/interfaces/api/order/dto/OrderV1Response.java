package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderV1Response(
    Long id,
    OrderStatus status,
    Long issuedCouponId,
    Long totalAmount,
    Long discountAmount,
    Long finalAmount,
    List<OrderItemV1Response> items,
    ZonedDateTime createdAt
) {
    public static OrderV1Response from(OrderInfo info) {
        return new OrderV1Response(
            info.id(),
            info.status(),
            info.issuedCouponId(),
            info.totalAmount(),
            info.discountAmount(),
            info.finalAmount(),
            info.items().stream().map(OrderItemV1Response::from).toList(),
            info.createdAt()
        );
    }

    public record OrderItemV1Response(
        Long productId,
        String productName,
        Long unitPrice,
        Integer quantity,
        Long subtotal
    ) {
        public static OrderItemV1Response from(OrderItemInfo info) {
            return new OrderItemV1Response(
                info.productId(),
                info.productName(),
                info.unitPrice(),
                info.quantity(),
                info.subtotal()
            );
        }
    }
}
