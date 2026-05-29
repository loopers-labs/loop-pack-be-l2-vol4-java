package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Long userId, List<OrderItem> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.PAID;
        validateItems(items);
        this.items.addAll(items);
    }

    public Long getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }

    /**
     * 주문 항목이 비어 있으면 안 된다는 불변식을 Order가 직접 보호한다.
     */
    public void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
    }

    /**
     * 상태 전이 규칙을 Order가 직접 보호한다.
     * CANCELLED → PAID 는 허용하지 않는다.
     */
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 취소된 주문입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean belongsTo(Long userId) {
        return this.userId.equals(userId);
    }
}
