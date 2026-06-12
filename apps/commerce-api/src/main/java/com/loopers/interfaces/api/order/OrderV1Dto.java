package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record OrderItemRequest(
        @NotNull @Min(1) Long productId,
        @NotNull @Min(1) Integer quantity
    ) {}

    public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items,
        Long couponId
    ) {
        public OrderCommand toCommand() {
            return new OrderCommand(
                items.stream()
                    .map(item -> new OrderCommand.Item(item.productId(), item.quantity()))
                    .toList(),
                couponId
            );
        }
    }

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
        Long originalTotalPrice,
        Long discountPrice,
        Long totalPrice,
        String status,
        ZonedDateTime createdAt,
        List<OrderLineResponse> lines
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.orderId(),
                info.userId(),
                info.originalTotalPrice(),
                info.discountPrice(),
                info.totalPrice(),
                info.status(),
                info.createdAt(),
                info.lines().stream().map(OrderLineResponse::from).toList()
            );
        }
    }
}
