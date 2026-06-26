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

    @Transient
    private List<OrderItem> items;

    public Order(Long userId, OrderStatus status, Money totalAmount, Money discountAmount, Money paymentAmount, List<OrderItem> items) {
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.paymentAmount = paymentAmount;
        this.items = items;
    }

    public static Order place(Long userId, List<OrderItem> items) {
        return place(userId, items, Money.ZERO);
    }

    public static Order place(Long userId, List<OrderItem> items, Money discountAmount) {
        validate(items);
        Money totalAmount = totalOf(items);
        Money paymentAmount = totalAmount.minus(discountAmount);
        return new Order(userId, OrderStatus.PENDING, totalAmount, discountAmount, paymentAmount, items);
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
