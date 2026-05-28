package com.loopers.application.order;

import com.loopers.domain.order.model.OrderItemStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderDetail(
    Long orderId,
    Long totalAmount,
    ZonedDateTime orderedAt,
    List<OrderItemInfo> items
) {
    public record OrderItemInfo(
        String productName,
        String brandName,
        Long price,
        int quantity,
        OrderItemStatus status
    ) {}
}
