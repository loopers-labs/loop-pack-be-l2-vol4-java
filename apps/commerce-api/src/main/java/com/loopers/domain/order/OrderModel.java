package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private List<OrderItemModel> orderItems = new ArrayList<>();

    private OrderModel(Long userId, List<OrderItemModel> items) {
        this.userId = userId;
        this.orderItems = new ArrayList<>(items);
        this.status = OrderStatus.PENDING;
        this.totalAmount = calculateTotalAmount();
    }

    public static OrderModel of(Long userId, List<OrderItemModel> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.");
        }
        return new OrderModel(userId, items);
    }

    public Long calculateTotalAmount() {
        return orderItems.stream().mapToLong(OrderItemModel::subtotal).sum();
    }

    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "PENDING 상태가 아닌 주문은 확정할 수 없습니다.");
        }
        this.status = OrderStatus.PAID;
    }

    public void fail() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "PENDING 상태가 아닌 주문은 실패 처리할 수 없습니다.");
        }
        this.status = OrderStatus.FAILED;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof OrderModel that)) {
            return false;
        }

        Long id = getId();
        Long otherId = that.getId();
        if (id == null || id == 0L || otherId == null || otherId == 0L) {
            return false;
        }
        return Objects.equals(id, otherId);
    }

    @Override
    public int hashCode() {
        return OrderModel.class.hashCode();
    }
}