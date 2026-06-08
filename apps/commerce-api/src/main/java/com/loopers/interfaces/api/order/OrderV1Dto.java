package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
        @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.") @Valid List<OrderItemRequest> items,
        Long couponId   // 미적용 시 null
    ) {}

    public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.") Long productId,
        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다.") int quantity
    ) {}

    public record OrderResponse(
        Long id,
        Long userId,
        String status,
        Long originalAmount,
        Long discountAmount,
        Long totalPrice,
        ZonedDateTime orderedAt,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> itemResponses = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.originalAmount(),
                info.discountAmount(),
                info.totalPrice(),
                info.orderedAt(),
                itemResponses
            );
        }
    }

    public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        Long productPrice,
        int quantity,
        Long subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.id(),
                item.productId(),
                item.productName(),
                item.productPrice(),
                item.quantity(),
                item.subtotal()
            );
        }
    }
}
