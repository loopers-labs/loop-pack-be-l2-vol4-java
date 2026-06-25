package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public class OrderEntity extends BaseEntity {

    private String userId;
    private OrderStatus status;
    private OrderSnapshot snapshot;

    protected OrderEntity() {}

    public OrderEntity(String userId, OrderSnapshot snapshot) {
        validateUserId(userId);
        validateSnapshot(snapshot);
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.snapshot = snapshot;
    }

    public static OrderEntity of(String id, String userId, OrderStatus status, OrderSnapshot snapshot,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        OrderEntity entity = new OrderEntity();
        entity.userId = userId;
        entity.status = status;
        entity.snapshot = snapshot;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public String getUserId() {
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

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }

    public void pay() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
        }
        this.status = OrderStatus.PAID;
    }

    private void validateUserId(String userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    private void validateSnapshot(OrderSnapshot snapshot) {
        if (snapshot == null || snapshot.items() == null || snapshot.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Set<String> productIds = new HashSet<>();
        for (OrderSnapshotItem item : snapshot.items()) {
            if (!productIds.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목에 중복된 상품이 있습니다.");
            }
        }
    }
}
