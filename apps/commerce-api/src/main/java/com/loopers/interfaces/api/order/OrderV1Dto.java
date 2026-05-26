package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
        @NotEmpty
        List<@Valid OrderProductRequest> items
    ) {
        public List<OrderProductCommand> toCommands() {
            return items.stream()
                .map(product -> new OrderProductCommand(product.productId(), product.quantity()))
                .toList();
        }
    }

    public record OrderProductRequest(
        @NotNull
        Long productId,
        @NotNull
        @Positive
        Integer quantity
    ) {
    }

    public record OrderResponse(
        Long id,
        String userLoginId,
        OrderStatus status,
        Long totalAmount,
        List<OrderLineResponse> orderLines,
        List<OrderFailureResponse> failures
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userLoginId(),
                info.status(),
                info.totalAmount(),
                info.orderLines().stream()
                    .map(OrderLineResponse::from)
                    .toList(),
                info.failures().stream()
                    .map(OrderFailureResponse::from)
                    .toList()
            );
        }
    }

    public record OrderLineResponse(
        Long productId,
        String productName,
        Long price,
        Integer quantity,
        Long amount
    ) {
        public static OrderLineResponse from(OrderInfo.OrderLineInfo info) {
            return new OrderLineResponse(
                info.productId(),
                info.productName(),
                info.price(),
                info.quantity(),
                info.amount()
            );
        }
    }

    public record OrderFailureResponse(
        Long productId,
        Integer quantity,
        String reason
    ) {
        public static OrderFailureResponse from(OrderInfo.OrderFailureInfo info) {
            return new OrderFailureResponse(
                info.productId(),
                info.quantity(),
                info.reason()
            );
        }
    }
}
