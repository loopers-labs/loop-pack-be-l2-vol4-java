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
 * 주문 엔티티. 여러 주문 항목을 모아 금액(원금/할인/최종)을 계산하고, 주문 상태를 책임진다.
 * 쿠폰이 적용된 경우 userCouponId 로 추적하며, 정책상 1주문 1쿠폰만 허용한다.
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

    /** 적용된 쿠폰(UserCoupon.id). 없으면 null. ID-only 참조이며 FK 제약은 두지 않는다. */
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    /** 할인 전 금액 = 모든 OrderItem.subtotal 합. */
    @Column(name = "original_amount", nullable = false)
    private Money originalAmount;

    /** 할인 금액. 쿠폰 미적용 시 0. */
    @Column(name = "discount_amount", nullable = false)
    private Money discountAmount;

    /** 최종 결제 금액 = originalAmount - discountAmount. Money 가 음수를 막아 안전하다. */
    @Column(name = "final_amount", nullable = false)
    private Money finalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Order(Long userId, List<OrderItem> items, Long userCouponId, Money discount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문에는 유저 정보가 필요합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.userCouponId = userCouponId;
        this.originalAmount = items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.zero(), Money::plus);
        this.discountAmount = (discount == null) ? Money.zero() : discount;
        this.finalAmount = this.originalAmount.minus(this.discountAmount);
        this.status = OrderStatus.CREATED;
    }

    /**
     * 주문 생성 정적 팩토리.
     * @param userCouponId 적용된 쿠폰 식별자(없으면 null)
     * @param discount     할인 금액(없으면 null 또는 Money.zero())
     */
    public static Order create(Long userId, List<OrderItem> items, Long userCouponId, Money discount) {
        return new Order(userId, items, userCouponId, discount);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
