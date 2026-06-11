package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
            @NotEmpty List<@Valid OrderItemRequest> items,
            Long couponId
    ) {
        public record OrderItemRequest(
                @NotNull Long productId,
                @Min(1) int quantity
        ) {}

        public List<OrderItemCommand> toCommands() {
            return items.stream()
                    .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
                    .toList();
        }
    }

    public record CreateOrderResponse(Long orderId) {
        public static CreateOrderResponse from(OrderInfo info) {
            return new CreateOrderResponse(info.orderId());
        }
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            Long productPrice,
            Integer quantity,
            Long subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                    item.productId(),
                    item.productName(),
                    item.productPrice(),
                    item.quantity(),
                    item.subtotal()
            );
        }
    }

    public record OrderResponse(
            Long orderId,
            OrderStatus status,
            Long originalAmount,
            Long discountAmount,
            Long finalAmount,
            Long couponId,
            List<OrderItemResponse> items,
            ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.orderId(),
                    info.status(),
                    info.originalAmount(),
                    info.discountAmount(),
                    info.finalAmount(),
                    info.couponId(),
                    info.items().stream().map(OrderItemResponse::from).toList(),
                    info.createdAt()
            );
        }
    }

    public record AdminOrderResponse(
            Long orderId,
            Long userId,
            OrderStatus status,
            Long originalAmount,
            Long discountAmount,
            Long finalAmount,
            Long couponId,
            List<OrderItemResponse> items,
            ZonedDateTime createdAt
    ) {
        public static AdminOrderResponse from(OrderInfo info) {
            return new AdminOrderResponse(
                    info.orderId(),
                    info.userId(),
                    info.status(),
                    info.originalAmount(),
                    info.discountAmount(),
                    info.finalAmount(),
                    info.couponId(),
                    info.items().stream().map(OrderItemResponse::from).toList(),
                    info.createdAt()
            );
        }
    }
}
