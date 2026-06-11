package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public class OrderEntity extends BaseEntity {

    private Long userId;
    private OrderStatus status;
    private OrderSnapshot snapshot;

    protected OrderEntity() {}

    public OrderEntity(Long userId, OrderSnapshot snapshot) {
        validateUserId(userId);
        validateSnapshot(snapshot);
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.snapshot = snapshot;
    }

    public static OrderEntity of(Long id, Long userId, OrderStatus status, OrderSnapshot snapshot,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        OrderEntity entity = new OrderEntity();
        entity.userId = userId;
        entity.status = status;
        entity.snapshot = snapshot;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OrderSnapshot getSnapshot() {
        return snapshot;
    }

    public Long finalAmount() {
        return snapshot.finalAmount();
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    private void validateSnapshot(OrderSnapshot snapshot) {
        if (snapshot == null || snapshot.items() == null || snapshot.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Set<Long> productIds = new HashSet<>();
        for (OrderSnapshotItem item : snapshot.items()) {
            if (!productIds.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목에 중복된 상품이 있습니다.");
            }
        }
    }
}
