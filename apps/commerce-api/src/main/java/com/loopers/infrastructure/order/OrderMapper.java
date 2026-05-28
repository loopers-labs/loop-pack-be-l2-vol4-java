package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderItemEntity;

import java.util.List;

public class OrderMapper {

    public static OrderJpaEntity toJpaEntity(OrderEntity order) {
        return new OrderJpaEntity(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getDeletedAt()
        );
    }

    public static OrderItemJpaEntity toItemJpaEntity(OrderItemEntity item, Long orderId) {
        return new OrderItemJpaEntity(
                item.getId(),
                orderId,
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice(),
                item.getQuantity(),
                item.getDeletedAt()
        );
    }

    public static OrderEntity toDomain(OrderJpaEntity order, List<OrderItemJpaEntity> items) {
        List<OrderItemEntity> domainItems = items.stream()
                .map(item -> OrderItemEntity.of(
                        item.getId(),
                        item.getOrderId(),
                        item.getProductId(),
                        item.getProductName(),
                        item.getProductPrice(),
                        item.getQuantity(),
                        item.getCreatedAt(),
                        item.getUpdatedAt(),
                        item.getDeletedAt()
                ))
                .toList();
        return OrderEntity.of(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                domainItems,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getDeletedAt()
        );
    }
}
