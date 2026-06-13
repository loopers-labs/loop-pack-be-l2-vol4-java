package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.Order;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record OrderCreateRequest(List<Item> items, Long couponId) {
        public record Item(Long productId, int quantity) {}

        public OrderCommand.Create toCommand(Long userId) {
            return new OrderCommand.Create(userId, couponId, items.stream()
                .map(item -> new OrderCommand.Create.Item(item.productId(), item.quantity()))
                .toList());
        }
    }

    public record OrderCreateResponse(Long orderId) {
        public static OrderCreateResponse from(OrderInfo.Create info) {
            return new OrderCreateResponse(info.orderId());
        }
    }

    public record OrderSummary(
            Long orderId,
            String status,
            BigDecimal originalPrice,
            BigDecimal discountAmount,
            BigDecimal totalPrice,
            ZonedDateTime createdAt
    ) {
        public static OrderSummary from(Order order) {
            return new OrderSummary(
                order.getId(),
                order.getStatus().name(),
                order.getOriginalPrice(),
                order.getDiscountAmount(),
                order.getTotalPrice(),
                order.getCreatedAt()
            );
        }
    }

    public record OrderResponse(
            Long orderId,
            String status,
            BigDecimal originalPrice,
            BigDecimal discountAmount,
            BigDecimal totalPrice,
            ZonedDateTime createdAt,
            List<OrderItemSummary> items
    ) {
        public static OrderResponse from(OrderInfo.Detail detail) {
            return new OrderResponse(
                detail.orderId(),
                detail.status(),
                detail.originalPrice(),
                detail.discountAmount(),
                detail.totalPrice(),
                detail.createdAt(),
                detail.items().stream().map(item ->
                    new OrderItemSummary(item.productId(), item.productName(), item.productPrice(), item.quantity())
                ).toList()
            );
        }
    }

    public record OrderItemSummary(
            Long productId,
            String productName,
            BigDecimal productPrice,
            int quantity
    ) {}
}
