package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderItemVO;

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

    public static OrderItemJpaVO toItemJpaEntity(OrderItemVO item, Long orderId) {
        return new OrderItemJpaVO(
                orderId,
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice(),
                item.getQuantity()
        );
    }

    public static OrderEntity toDomain(OrderJpaEntity order, List<OrderItemJpaVO> items) {
        List<OrderItemVO> domainItems = items.stream()
                .map(item -> OrderItemVO.of(
                        item.getOrderId(),
                        item.getProductId(),
                        item.getProductName(),
                        item.getProductPrice(),
                        item.getQuantity()
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
