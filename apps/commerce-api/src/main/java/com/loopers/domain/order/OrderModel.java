package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.domain.common.MoneyConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_amount", nullable = false)
    private Money totalAmount;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "discount_amount", nullable = false)
    private Money discountAmount;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "final_amount", nullable = false)
    private Money finalAmount;

    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private final List<OrderItem> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId, List<OrderItem> items, Long issuedCouponId, Money discountAmount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 비어있을 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.CREATED;
        items.forEach(this::addItem);
        this.totalAmount = this.items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.ZERO, Money::add);
        this.issuedCouponId = issuedCouponId;
        this.discountAmount = discountAmount == null ? Money.ZERO : discountAmount;
        if (issuedCouponId == null && this.discountAmount.value() > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 없이는 할인을 적용할 수 없습니다.");
        }
        this.finalAmount = this.totalAmount.subtract(this.discountAmount);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void addItem(OrderItem item) {
        this.items.add(item);
        item.assignTo(this);
    }
}
