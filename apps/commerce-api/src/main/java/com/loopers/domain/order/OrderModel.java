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

    private BigDecimal originalPrice;

    private BigDecimal discountAmount;

    private BigDecimal finalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {
    }

    public OrderModel(Long userId, BigDecimal originalPrice, BigDecimal discountAmount, List<OrderItemModel> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 정보가 없습니다.");
        }
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 0 이상이어야 합니다.");
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 없습니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.PLACED;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.finalPrice = originalPrice.subtract(discountAmount);
        this.items = new ArrayList<>(items);
        this.items.forEach(item -> item.assignOrder(this));
    }

    public static OrderModel create(Long userId, List<OrderItemData> itemDataList, BigDecimal discountAmount) {
        List<OrderItemModel> items = itemDataList.stream()
                .map(d -> new OrderItemModel(d.productId(), d.productName(), d.productPrice(), d.quantity()))
                .toList();
        BigDecimal originalPrice = items.stream()
                .map(OrderItemModel::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderModel(userId, originalPrice, discountAmount, items);
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "해당 주문에 접근할 수 없습니다.");
        }
    }
}
