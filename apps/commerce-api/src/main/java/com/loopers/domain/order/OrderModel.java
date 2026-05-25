package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    // 아그리거트 루트의 composition — 주문 로드 시 항목은 항상 함께 필요하므로 EAGER (단건 조회 위주)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId, PaymentMethod paymentMethod) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (paymentMethod == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 수단은 null일 수 없습니다.");
        }
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.status = OrderStatus.PENDING;
        this.totalAmount = Money.zero();
    }

    public void addItem(OrderItem item) {
        if (item == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 null일 수 없습니다.");
        }
        this.items.add(item);
    }

    /** lineTotal 합산 → totalAmount (03 §4, Order 책임). */
    public void calculateTotals() {
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(Money.zero(), Money::add);
    }

    /** PENDING → PAID. 다른 상태에서 호출 시 CONFLICT (04 §5.1). */
    public void markPaid() {
        requirePending();
        this.status = OrderStatus.PAID;
        this.paidAt = ZonedDateTime.now();
    }

    /** PENDING → FAILED. 다른 상태에서 호출 시 CONFLICT. 재고 원복은 Service 책임. */
    public void markFailed(String reason) {
        requirePending();
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
    }

    private void requirePending() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "PENDING 상태에서만 결제 결과를 반영할 수 있습니다. (현재: " + this.status + ")");
        }
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public ZonedDateTime getPaidAt() {
        return paidAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
