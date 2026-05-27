package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record OrderItemRequest(
        @NotNull Long productId,
        @Min(1) int quantity
    ) {
        public OrderItemCommand toCommand() {
            return new OrderItemCommand(productId, quantity);
        }
    }

    public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
    ) {
        public OrderCreateCommand toCommand(Long userId) {
            return new OrderCreateCommand(
                userId,
                items.stream().map(OrderItemRequest::toCommand).toList()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        int unitPrice,
        String brandName,
        int quantity,
        int totalPrice
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(),
                info.productName(),
                info.unitPrice(),
                info.brandName(),
                info.quantity(),
                info.totalPrice()
            );
        }
    }

    public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        int totalAmount,
        List<OrderItemResponse> items,
        ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.totalAmount(),
                info.items().stream().map(OrderItemResponse::from).toList(),
                info.createdAt()
            );
        }
    }
}
