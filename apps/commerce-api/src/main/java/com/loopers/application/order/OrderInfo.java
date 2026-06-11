package com.loopers.application.order;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderSnapshot;
import com.loopers.domain.order.OrderSnapshotItem;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long orderId,
        Long userId,
        OrderStatus status,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        Long couponId,
        List<OrderItemInfo> items,
        ZonedDateTime createdAt
) {
    public record OrderItemInfo(
            Long productId,
            String productName,
            Long productPrice,
            Integer quantity,
            Long subtotal
    ) {
        public static OrderItemInfo from(OrderSnapshotItem item) {
            return new OrderItemInfo(
                    item.productId(),
                    item.productName(),
                    item.productPrice(),
                    item.quantity(),
                    item.subtotal()
            );
        }
    }

    public static OrderInfo from(OrderEntity order) {
        OrderSnapshot snapshot = order.getSnapshot();
        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                snapshot.originalAmount(),
                snapshot.discountAmount(),
                snapshot.finalAmount(),
                snapshot.couponId(),
                snapshot.items().stream().map(OrderItemInfo::from).toList(),
                order.getCreatedAt()
        );
    }
}
