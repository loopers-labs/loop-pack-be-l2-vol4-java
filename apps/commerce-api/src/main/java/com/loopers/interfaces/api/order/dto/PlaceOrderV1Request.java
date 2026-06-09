package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.OrderLineCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record PlaceOrderV1Request(
    @NotEmpty @Valid List<OrderLineV1Request> items,
    @Positive Long couponId
) {
    public List<OrderLineCommand> toCommands() {
        return items.stream().map(OrderLineV1Request::toCommand).toList();
    }

    public record OrderLineV1Request(
        @NotNull @Positive Long productId,
        @NotNull @Positive Integer quantity
    ) {
        public OrderLineCommand toCommand() {
            return new OrderLineCommand(productId, quantity);
        }
    }
}
