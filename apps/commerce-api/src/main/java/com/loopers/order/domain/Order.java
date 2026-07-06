package com.loopers.order.domain;

import com.loopers.common.domain.Money;
import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 사용자 주문을 표현하는 Aggregate Root.
 * OrderItem 은 같은 Aggregate 의 구성요소지만 객체로 소유하지 않고 orderId 참조로만 연결하며,
 * 영속성은 별도 테이블/Repository 로 분리한다. 총액은 생성 시 항목 소계의 합으로 산정해 함께 저장한다.
 */
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "final_amount", nullable = false))
    private Money finalAmount;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Embedded
    private ShippingDestination shippingDestination;

    @Column(name = "ordered_at", nullable = false)
    private ZonedDateTime orderedAt;

    private Order(
            Long userId,
            String orderNumber,
            ShippingDestination shippingDestination,
            List<OrderItem> items
    ) {
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.shippingDestination = shippingDestination;
        validate(items);
        this.totalAmount = items.stream().map(OrderItem::subtotal).reduce(Money.ZERO, Money::plus);
        this.discountAmount = Money.ZERO;
        this.finalAmount = this.totalAmount;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.orderedAt = ZonedDateTime.now();
    }

    public static Order create(
            Long userId,
            String orderNumber,
            ShippingDestination shippingDestination,
            List<OrderItem> items
    ) {
        return new Order(userId, orderNumber, shippingDestination, items);
    }

    public void applyDiscount(Long userCouponId, Money discountAmount) {
        Money effectiveDiscount = discountAmount.value() > totalAmount.value() ? totalAmount : discountAmount;
        this.userCouponId = userCouponId;
        this.discountAmount = effectiveDiscount;
        this.finalAmount = Money.of(totalAmount.value() - effectiveDiscount.value());
    }

    public void markPaid() {
        if (!isPayable()) {
            return;
        }
        this.status = OrderStatus.PAID;
    }

    public void markPaymentFailed() {
        if (!isPayable()) {
            return;
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public boolean isPayable() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    private void validate(List<OrderItem> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId 는 비어있을 수 없습니다.");
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문번호는 비어있을 수 없습니다.");
        }
        if (shippingDestination == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "배송지 정보는 비어있을 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 하나 이상이어야 합니다.");
        }
    }
}
