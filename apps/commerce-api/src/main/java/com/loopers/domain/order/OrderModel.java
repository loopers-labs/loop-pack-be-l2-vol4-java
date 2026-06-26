package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.domain.order.vo.Money;
import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uq_order_order_number", columnNames = {"order_number"}),
        indexes = {
                @Index(name = "idx_orders_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_orders_user_coupon_id", columnList = "user_coupon_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModel extends BaseEntity {

    @Column(name = "order_number", nullable = false, length = 20)
    private String orderNumber;

    @Column(nullable = false)
    private Long userId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "original_amount", nullable = false))
    private Money originalAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_amount", nullable = false))
    private Money totalMoney;

    @Column
    private Long userCouponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> items = new ArrayList<>();

    public OrderModel(String orderNumber, Long userId, Long userCouponId) {
        Guard.notNull(orderNumber, "주문 번호는 필수입니다.");
        Guard.notNull(userId, "사용자 ID는 필수입니다.");
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.userCouponId = userCouponId;
        this.originalAmount = new Money(0L);
        this.discountAmount = new Money(0L);
        this.totalMoney = new Money(0L);
        this.status = OrderStatus.REQUESTED;
    }

    public void applyAmounts(Money originalAmount, Money discountAmount) {
        Guard.notNull(originalAmount, "원금은 필수입니다.");
        Guard.notNull(discountAmount, "할인 금액은 필수입니다.");
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.totalMoney = new Money(originalAmount.getValue() - discountAmount.getValue());
    }

    public void complete() {
        if (this.status == OrderStatus.COMPLETED) return;
        if (this.status != OrderStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상태에서만 완료 처리할 수 있습니다.");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status != OrderStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상태에서만 취소할 수 있습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isPayable() {
        return this.status == OrderStatus.REQUESTED;
    }

    public void addItem(OrderItemModel item) {
        this.items.add(item);
    }

    public String getOrderNumber() { return orderNumber; }

    public Long getUserId() { return userId; }

    public Money getOriginalAmount() { return originalAmount; }

    public Money getDiscountAmount() { return discountAmount; }

    public Money getTotalMoney() { return totalMoney; }

    public Long getUserCouponId() { return userCouponId; }

    public OrderStatus getStatus() { return status; }

    public List<OrderItemModel> getItems() { return items; }
}
