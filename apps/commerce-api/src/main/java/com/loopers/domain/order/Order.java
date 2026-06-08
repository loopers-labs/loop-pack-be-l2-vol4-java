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

import java.math.BigDecimal;
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

    @Transient
    private List<OrderItem> items;

    public Order(Long userId, OrderStatus status, Money totalAmount, List<OrderItem> items) {
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    public static Order place(Long userId, List<OrderItem> items) {
        validate(items);
        Money totalAmount = items.stream()
            .map(OrderItem::lineAmount)
            .reduce(new Money(BigDecimal.ZERO), Money::plus);
        return new Order(userId, OrderStatus.COMPLETED, totalAmount, items);
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
