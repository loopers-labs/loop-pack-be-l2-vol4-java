package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderLineCommand;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(@NotEmpty @Valid List<Line> items) {
        public record Line(
            @NotNull @Positive Long productId,
            @NotNull @Positive Integer quantity
        ) {
            public OrderLineCommand toCommand() {
                return new OrderLineCommand(productId, quantity);
            }
        }

        public List<OrderLineCommand> toCommands() {
            return items == null ? List.of() : items.stream().map(Line::toCommand).toList();
        }
    }

    public record ItemResponse(
        Long productId,
        String productName,
        Long unitPrice,
        Integer quantity,
        Long subtotal
    ) {
        public static ItemResponse from(OrderItemInfo info) {
            return new ItemResponse(info.productId(), info.productName(), info.unitPrice(), info.quantity(), info.subtotal());
        }
    }

    public record Response(
        Long id,
        Long userId,
        OrderStatus status,
        Long totalAmount,
        List<ItemResponse> items,
        ZonedDateTime createdAt
    ) {
        public static Response from(OrderInfo info) {
            return new Response(
                info.id(),
                info.userId(),
                info.status(),
                info.totalAmount(),
                info.items().stream().map(ItemResponse::from).toList(),
                info.createdAt()
            );
        }
    }
}
