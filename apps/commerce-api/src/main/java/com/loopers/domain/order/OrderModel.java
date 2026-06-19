package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "final_amount", nullable = false))
    private Money finalAmount;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId, List<OrderItemModel> items) {
        this(userId, items, Money.ZERO, null);
    }

    public OrderModel(Long userId, List<OrderItemModel> items, Money discountAmount, Long userCouponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.status = OrderStatus.PENDING;
        this.totalAmount = this.items.stream()
            .map(OrderItemModel::subtotal)
            .reduce(Money.ZERO, Money::plus);
        Money discount = (discountAmount != null) ? discountAmount : Money.ZERO;
        // 할인은 총액을 상한으로 한다 (최종 금액 음수 방지)
        this.discountAmount = Money.of(Math.min(discount.getAmount(), this.totalAmount.getAmount()));
        this.finalAmount = this.totalAmount.minus(this.discountAmount);
        this.userCouponId = userCouponId;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getTotalAmount() {
        return totalAmount.getAmount();
    }

    public Long getDiscountAmount() {
        return discountAmount.getAmount();
    }

    public Long getFinalAmount() {
        return finalAmount.getAmount();
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public List<OrderItemModel> getItems() {
        return Collections.unmodifiableList(items);
    }
}
