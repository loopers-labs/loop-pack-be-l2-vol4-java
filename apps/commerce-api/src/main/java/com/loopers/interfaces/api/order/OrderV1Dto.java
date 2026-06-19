package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
        @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.")
        @Valid
        List<Item> items,

        /** 적용할 쿠폰(UserCoupon.id). 미적용 시 null. */
        Long couponId
    ) {
        public record Item(
            @NotNull(message = "productId 는 필수입니다.")
            Long productId,
            @Min(value = 1, message = "quantity 는 1 이상이어야 합니다.")
            int quantity
        ) {}
    }

    public record OrderResponse(
        Long orderId,
        Long userId,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        Long userCouponId,
        String status,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.orderId(),
                info.userId(),
                info.originalAmount(),
                info.discountAmount(),
                info.finalAmount(),
                info.userCouponId(),
                info.status(),
                items
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        Long unitPrice,
        int quantity,
        Long subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.productId(),
                item.productName(),
                item.unitPrice(),
                item.quantity(),
                item.subtotal()
            );
        }
    }
}
