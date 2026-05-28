package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OrderEntity extends BaseEntity {

    private Long userId;
    private OrderStatus status;
    private List<OrderItemEntity> items;

    protected OrderEntity() {}

    public OrderEntity(Long userId, List<OrderItemEntity> items) {
        validateUserId(userId);
        validateItems(items);
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.items = items;
    }

    public static OrderEntity of(Long id, Long userId, OrderStatus status, List<OrderItemEntity> items,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        OrderEntity entity = new OrderEntity();
        entity.userId = userId;
        entity.status = status;
        entity.items = items;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItemEntity> getItems() {
        return items;
    }

    public Long calculateTotalAmount() {
        return items.stream()
                .mapToLong(OrderItemEntity::subtotal)
                .sum();
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    private void validateItems(List<OrderItemEntity> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Set<Long> productIds = new HashSet<>();
        for (OrderItemEntity item : items) {
            if (!productIds.add(item.getProductId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목에 중복된 상품이 있습니다.");
            }
        }
    }
}
