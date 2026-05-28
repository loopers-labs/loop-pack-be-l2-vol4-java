package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
public class OrderModel extends BaseEntity {

    private Long userId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {
    }

    public OrderModel(Long userId, BigDecimal totalPrice, List<OrderItemModel> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 정보가 없습니다.");
        }
        if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 0 이상이어야 합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 없습니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.PLACED;
        this.totalPrice = totalPrice;
        this.items = new ArrayList<>(items);
        this.items.forEach(item -> item.assignOrder(this));
    }

    public static OrderModel create(Long userId, List<OrderItemData> itemDataList) {
        List<OrderItemModel> items = itemDataList.stream()
                .map(d -> new OrderItemModel(d.productId(), d.productName(), d.productPrice(), d.quantity()))
                .toList();
        BigDecimal totalPrice = items.stream()
                .map(OrderItemModel::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderModel(userId, totalPrice, items);
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "해당 주문에 접근할 수 없습니다.");
        }
    }
}
