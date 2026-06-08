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

    /** 주문에 적용된 발급 쿠폰 id. 미적용 시 null. 쿠폰 → 주문 역추적의 키. 쿠폰은 다른 애그리거트라 ID 참조. */
    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    /** 쿠폰 적용 전 금액 — 항목 subtotal 합계. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_amount", nullable = false)
    private Money totalAmount;

    /** 쿠폰 할인 금액(주문 전체 기준). 미적용 시 0. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "discount_amount", nullable = false)
    private Money discountAmount;

    /** 최종 결제 금액 = totalAmount - discountAmount. */
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
        // 할인액이 주문 총액을 초과하면 Money.subtract가 BAD_REQUEST로 막는다.
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
