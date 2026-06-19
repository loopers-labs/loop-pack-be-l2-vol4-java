package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주문 엔티티. 여러 주문 항목을 모아 총 금액을 계산하고, 주문 상태를 책임진다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    private Long userId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "total_price", nullable = false)
    private Money totalPrice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Order(Long userId, List<OrderItem> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문에는 유저 정보가 필요합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.totalPrice = calculateTotalPrice(items);
        this.status = OrderStatus.CREATED;
    }

    public static Order create(Long userId, List<OrderItem> items) {
        return new Order(userId, items);
    }

    private static Money calculateTotalPrice(List<OrderItem> items) {
        return items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.zero(), Money::plus);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
