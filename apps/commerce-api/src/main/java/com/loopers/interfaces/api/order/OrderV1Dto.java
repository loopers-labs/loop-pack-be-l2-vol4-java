package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record PlaceOrderRequest(
            List<OrderItemDto> items,
            Long couponId
    ) {}

    public record OrderItemDto(
            Long productId,
            Integer quantity
    ) {}

    public record OrderResponse(
            Long id,
            Long memberId,
            OrderStatus status,
            Long originalAmount,
            Long discountAmount,
            Long totalAmount,
            Long couponId,
            ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.memberId(),
                    info.status(),
                    info.originalAmount(),
                    info.discountAmount(),
                    info.totalAmount(),
                    info.couponId(),
                    info.createdAt()
            );
        }
    }
}
