package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.user.UserModel;

import java.time.ZonedDateTime;
import java.util.List;

public record AdminOrderInfo(
    Long id,
    Long userId,
    String buyerLoginId,
    Long totalAmount,
    OrderStatus status,
    List<OrderInfo.Item> items,
    ZonedDateTime createdAt
) {
    public static AdminOrderInfo from(OrderModel order, List<OrderItem> items, UserModel buyer) {
        return new AdminOrderInfo(
            order.getId(),
            order.getUserId(),
            buyer.getLoginId().getValue(),
            order.getTotalAmount(),
            order.getStatus(),
            items.stream().map(OrderInfo.Item::from).toList(),
            order.getCreatedAt()
        );
    }
}
