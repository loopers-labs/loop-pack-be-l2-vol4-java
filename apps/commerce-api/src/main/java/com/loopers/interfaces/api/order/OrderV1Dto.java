package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(
            @NotEmpty @Valid List<OrderItemRequest> items
    ) {
    }

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Long quantity
    ) {
    }

    public record OrderResponse(
            Long id,
            String status,
            BigDecimal totalPrice,
            List<OrderItemResponse> items,
            ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.status(),
                    info.totalPrice(),
                    info.items().stream().map(OrderItemResponse::from).toList(),
                    info.createdAt()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            BigDecimal productPrice,
            Long quantity,
            BigDecimal subtotal
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                    info.productId(),
                    info.productName(),
                    info.productPrice(),
                    info.quantity(),
                    info.subtotal()
            );
        }
    }
}
