package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record OrderInfo(
    UUID id,
    UUID userId,
    OrderStatus status,
    Long pgAmount,
    ShippingInfoValue shippingInfo,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {

    public record ShippingInfoValue(
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String detailAddress
    ) {}

    public record OrderItemInfo(
        UUID id,
        UUID productId,
        String productName,
        String brandName,
        Long price,
        int quantity,
        long subtotal
    ) {}

    public static OrderInfo from(OrderModel order) {
        ShippingInfoValue shippingInfo = new ShippingInfoValue(
            order.getShippingInfo().getReceiverName(),
            order.getShippingInfo().getReceiverPhone(),
            order.getShippingInfo().getZipCode(),
            order.getShippingInfo().getAddress(),
            order.getShippingInfo().getDetailAddress()
        );

        List<OrderItemInfo> items = order.getItems().stream()
            .map(item -> new OrderItemInfo(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getBrandName(),
                item.getPrice(),
                item.getQuantity(),
                item.getSubtotal()
            ))
            .toList();

        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getPgAmount(),
            shippingInfo,
            items,
            order.getCreatedAt()
        );
    }
}
