package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "failure_reason")
    private String failureReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId, List<OrderItem> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 비어있을 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.CREATED;
        for (OrderItem item : items) {
            item.assignOrder(this);
            this.items.add(item);
        }
        this.totalAmount = items.stream().mapToLong(OrderItem::subtotal).sum();
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void markSucceeded() {
        ensureCreated("SUCCEEDED");
        this.status = OrderStatus.SUCCEEDED;
    }

    public void markFailed(String reason) {
        ensureCreated("FAILED");
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
    }

    private void ensureCreated(String target) {
        if (this.status != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.CONFLICT,
                "[status = " + this.status + "] CREATED 상태에서만 " + target + "로 전이할 수 있습니다.");
        }
    }
}
