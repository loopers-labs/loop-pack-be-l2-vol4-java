package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderEntity;

public class OrderMapper {

    public static OrderJpaEntity toJpaEntity(OrderEntity order) {
        return new OrderJpaEntity(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getSnapshot(),
                order.getDeletedAt()
        );
    }

    public static OrderEntity toDomain(OrderJpaEntity order) {
        return OrderEntity.of(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getSnapshot(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getDeletedAt()
        );
    }
}
