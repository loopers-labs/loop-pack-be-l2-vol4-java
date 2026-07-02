package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount = 0L;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount = 0L;

    @Column(name = "payment_amount", nullable = false)
    private Long paymentAmount = 0L;

    @Column(name = "applied_member_coupon_id")
    private Long appliedMemberCouponId;

    protected Order() {}

    private Order(Long memberId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자는 필수입니다.");
        }
        this.memberId = memberId;
    }

    public static Order create(Long memberId) {
        return new Order(memberId);
    }

    /** 주문 항목을 추가한다. 내부 OrderItem 생성은 Aggregate Root 를 통해서만 이루어진다. */
    public void addItem(OrderItemSnapshot snapshot, int quantity) {
        OrderItem item = new OrderItem(this, snapshot, quantity);
        this.items.add(item);
        this.totalAmount += item.getLineAmount();
        this.paymentAmount = this.totalAmount - this.discountAmount;
    }

    /** 쿠폰 할인을 적용한다. 결제 예정 금액 = 총액 - 할인액(최소 0). */
    public void applyCoupon(Long memberCouponId, long discount) {
        applyDiscount(discount);
        this.appliedMemberCouponId = memberCouponId;
    }

    /** 할인 금액을 확정하고 결제 예정 금액을 재계산한다. 쿠폰 미적용 주문은 discount=0 으로 호출한다. */
    public void applyDiscount(long discount) {
        if (discount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }
        this.discountAmount = discount;
        this.paymentAmount = Math.max(0L, this.totalAmount - discount);
    }

    public boolean belongsTo(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
