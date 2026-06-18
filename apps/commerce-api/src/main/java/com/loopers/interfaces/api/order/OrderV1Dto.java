package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class OrderV1Dto {

    public record CreateRequest(
        @NotNull @Valid ShippingInfoRequest shippingInfo,
        @NotEmpty List<@Valid OrderItemRequest> items,
        UUID couponId
    ) {
        /** 쿠폰 미적용 편의 생성자 */
        public CreateRequest(ShippingInfoRequest shippingInfo, List<OrderItemRequest> items) {
            this(shippingInfo, items, null);
        }
    }

    public record ShippingInfoRequest(
        @NotBlank String receiverName,
        @NotBlank String receiverPhone,
        @NotBlank String zipCode,
        @NotBlank String address,
        String detailAddress
    ) {}

    public record OrderItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) Integer quantity
    ) {}

    public record OrderResponse(
        UUID id,
        OrderStatus status,
        Long originalAmount,
        Long discountAmount,
        Long pgAmount,
        UUID couponId,
        ShippingInfoResponse shippingInfo,
        List<OrderItemResponse> items,
        ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.status(),
                info.originalAmount(),
                info.discountAmount(),
                info.pgAmount(),
                info.couponId(),
                ShippingInfoResponse.from(info.shippingInfo()),
                info.items().stream().map(OrderItemResponse::from).toList(),
                info.createdAt()
            );
        }
    }

    public record ShippingInfoResponse(
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String detailAddress
    ) {
        public static ShippingInfoResponse from(OrderInfo.ShippingInfoValue v) {
            return new ShippingInfoResponse(v.receiverName(), v.receiverPhone(), v.zipCode(), v.address(), v.detailAddress());
        }
    }

    public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String brandName,
        Long price,
        int quantity,
        long subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.id(), item.productId(), item.productName(),
                item.brandName(), item.price(), item.quantity(), item.subtotal()
            );
        }
    }

    /** 어드민용 — userId 포함 */
    public record AdminOrderResponse(
        UUID id,
        UUID userId,
        OrderStatus status,
        Long originalAmount,
        Long discountAmount,
        Long pgAmount,
        UUID couponId,
        ShippingInfoResponse shippingInfo,
        List<OrderItemResponse> items,
        ZonedDateTime createdAt
    ) {
        public static AdminOrderResponse from(OrderInfo info) {
            return new AdminOrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.originalAmount(),
                info.discountAmount(),
                info.pgAmount(),
                info.couponId(),
                ShippingInfoResponse.from(info.shippingInfo()),
                info.items().stream().map(OrderItemResponse::from).toList(),
                info.createdAt()
            );
        }
    }
}
