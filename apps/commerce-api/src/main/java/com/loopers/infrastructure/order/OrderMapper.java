package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.product.Money;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class OrderMapper {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toDomainItem)
                .toList();
        return Order.restore(
                entity.getId(),
                entity.getUserId(),
                items,
                Money.of(entity.getTotalAmount()),
                entity.getStatus(),
                entity.getCreatedAt().withZoneSameInstant(ZONE).toLocalDateTime()
        );
    }

    public OrderJpaEntity toJpaEntity(Order domain) {
        List<OrderItemJpaEntity> items = domain.getItems().stream()
                .map(this::toJpaItem)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return OrderJpaEntity.of(
                domain.getUserId(),
                domain.getStatus(),
                domain.getTotalAmount().getAmount(),
                items
        );
    }

    private OrderItem toDomainItem(OrderItemJpaEntity entity) {
        return OrderItem.of(
                entity.getProductId(),
                entity.getProductName(),
                Money.of(entity.getUnitPrice()),
                entity.getQuantity()
        );
    }

    private OrderItemJpaEntity toJpaItem(OrderItem domain) {
        return OrderItemJpaEntity.of(
                domain.getProductId(),
                domain.getProductName(),
                domain.getUnitPrice().getAmount(),
                domain.getQuantity()
        );
    }
}
