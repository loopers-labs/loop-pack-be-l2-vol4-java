package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    protected OrderModel() {}

    private OrderModel(Long userId, Long totalAmount, OrderStatus status) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public static OrderModel create(Long userId, OrderLines lines) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID 는 비어있을 수 없습니다.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.EMPTY_ORDER_ITEMS, "주문 항목이 비어있습니다.");
        }
        return new OrderModel(userId, lines.totalAmount(), OrderStatus.CREATED);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
