package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.money.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount",
        column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Embedded
    @AttributeOverride(name = "amount",
        column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "amount",
        column = @Column(name = "payment_amount", nullable = false))
    private Money paymentAmount;

    // 결제 실패 시 쿠폰을 원복하려면 어떤 쿠폰을 썼는지 알아야 한다. 사용한 UserCoupon id. 쿠폰 미사용이면 null.
    @Column
    private Long couponId;

    @Transient
    private List<OrderItem> items;

    public Order(Long userId, OrderStatus status, Money totalAmount, Money discountAmount, Money paymentAmount, Long couponId, List<OrderItem> items) {
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.paymentAmount = paymentAmount;
        this.couponId = couponId;
        this.items = items;
    }

    public static Order place(Long userId, List<OrderItem> items) {
        return place(userId, items, Money.ZERO, null);
    }

    public static Order place(Long userId, List<OrderItem> items, Money discountAmount) {
        return place(userId, items, discountAmount, null);
    }

    public static Order place(Long userId, List<OrderItem> items, Money discountAmount, Long couponId) {
        validate(items);
        Money totalAmount = totalOf(items);
        Money paymentAmount = totalAmount.minus(discountAmount);
        return new Order(userId, OrderStatus.PENDING, totalAmount, discountAmount, paymentAmount, couponId, items);
    }

    /** 결제 성공 확정. 결제 대기(PENDING) 상태에서만 가능 — 중복 콜백/폴링에도 한 번만 전이되도록 가드한다. */
    public void markPaid() {
        requirePending();
        this.status = OrderStatus.PAID;
    }

    /** 결제 실패 확정. 보상(재고·쿠폰 원복)은 호출자(application)가 이 전이에 성공했을 때만 수행한다. */
    public void markFailed() {
        requirePending();
        this.status = OrderStatus.FAILED;
    }

    private void requirePending() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "결제 대기 상태의 주문만 확정할 수 있습니다. 현재 상태: " + this.status);
        }
    }

    public static Money totalOf(List<OrderItem> items) {
        return items.stream()
            .map(OrderItem::lineAmount)
            .reduce(Money.ZERO, Money::plus);
    }

    private static void validate(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
    }

    public void assignItems(List<OrderItem> items) {
        this.items = items;
    }
}
