package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.OrderLineCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record PlaceOrderV1Request(
    @NotEmpty @Valid List<OrderLineV1Request> items
) {
    public List<OrderLineCommand> toCommands() {
        return items.stream().map(OrderLineV1Request::toCommand).toList();
    }

    /** couponId는 해당 항목에 적용할 발급 쿠폰 id. 미적용 시 생략(null). 주문 1건당 1장만 허용된다. */
    public record OrderLineV1Request(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity,
        Long couponId
    ) {
        public OrderLineCommand toCommand() {
            return new OrderLineCommand(productId, quantity, couponId);
        }
    }
}
