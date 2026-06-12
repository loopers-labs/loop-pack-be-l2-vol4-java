package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order Aggregate — OrderItem 들을 포함하고 총액·상태 전이를 관리한다.
 */
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId, List<OrderItemModel> items) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 양수여야 합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1 개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.totalPrice = computeTotal(this.items);
        this.status = OrderStatus.PENDING;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }

    public List<OrderItemModel> getItems() {
        return Collections.unmodifiableList(items);
    }

    public long totalAmount() {
        return totalPrice;
    }

    public void markPaid() {
        assertNotTerminal();
        this.status = OrderStatus.PAID;
    }

    public void markFailed() {
        assertNotTerminal();
        this.status = OrderStatus.FAILED;
    }

    public void markCancelled() {
        assertNotTerminal();
        this.status = OrderStatus.CANCELLED;
    }

    private void assertNotTerminal() {
        if (this.status != null && this.status.isTerminal()) {
            throw new CoreException(ErrorType.CONFLICT,
                "이미 종료 상태(" + this.status + ")인 주문은 다시 전이할 수 없습니다.");
        }
    }

    private static long computeTotal(List<OrderItemModel> items) {
        long total = 0L;
        for (OrderItemModel item : items) {
            total += item.subtotal();
        }
        return total;
    }
}
