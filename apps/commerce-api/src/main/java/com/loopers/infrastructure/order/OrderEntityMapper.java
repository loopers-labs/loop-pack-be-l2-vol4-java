package com.loopers.infrastructure.order;

import com.loopers.domain.order.Money;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;

import java.util.List;

/**
 * OrderModel(순수 도메인) ↔ OrderEntity(JPA) 변환기. 도메인과 영속 경계 사이의 번역만 담당한다.
 * Money VO는 엔티티에서 primitive(Long) 컬럼으로 풀어 저장하고, 복원 시 다시 Money로 감싼다.
 */
public final class OrderEntityMapper {

    private OrderEntityMapper() {}

    public static OrderEntity toEntity(OrderModel order) {
        List<OrderItemEntity> itemEntities = order.getItems().stream()
                .map(OrderEntityMapper::toItemEntity)
                .toList();
        return new OrderEntity(
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount().getAmount(),
                order.getDiscountAmount().getAmount(),
                order.getFinalAmount().getAmount(),
                order.getUserCouponId(),
                order.getPaymentMethod(),
                order.getFailureReason(),
                order.getPaidAt(),
                itemEntities
        );
    }

    private static OrderItemEntity toItemEntity(OrderItem item) {
        return new OrderItemEntity(
                item.getProductId(),
                item.getProductName(),
                item.getBrandName(),
                item.getImageUrl(),
                item.getUnitPrice().getAmount(),
                item.getQuantity(),
                item.getLineTotal().getAmount()
        );
    }

    public static OrderModel toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(OrderEntityMapper::toItemDomain)
                .toList();
        return OrderModel.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getStatus(),
                new Money(entity.getTotalAmount()),
                new Money(entity.getDiscountAmount()),
                new Money(entity.getFinalAmount()),
                entity.getUserCouponId(),
                entity.getPaymentMethod(),
                entity.getFailureReason(),
                entity.getPaidAt(),
                items
        );
    }

    private static OrderItem toItemDomain(OrderItemEntity entity) {
        return new OrderItem(
                entity.getProductId(),
                entity.getProductName(),
                entity.getBrandName(),
                entity.getImageUrl(),
                new Money(entity.getUnitPrice()),
                entity.getQuantity()
        );
    }
}
