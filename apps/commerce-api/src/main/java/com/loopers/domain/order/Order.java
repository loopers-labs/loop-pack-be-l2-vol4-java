package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_total_price", nullable = false)
    private long orderTotalPrice;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    private Order(Long userId, List<OrderItem> items) {
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.orderTotalPrice = calculateTotalPrice(items);
    }

    public static Order create(Long userId, List<OrderItem> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 비어있을 수 없습니다.");
        }
        validateItems(items);
        return new Order(userId, items);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    private static void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Set<Long> productIds = items.stream()
            .map(OrderItem::getProductId)
            .collect(Collectors.toSet());
        if (productIds.size() != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "같은 상품을 중복 주문할 수 없습니다.");
        }
    }

    private static long calculateTotalPrice(List<OrderItem> items) {
        return items.stream()
            .mapToLong(OrderItem::getTotalPrice)
            .sum();
    }
}
