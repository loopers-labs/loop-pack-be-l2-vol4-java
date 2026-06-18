package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "final_amount", nullable = false)
    private Long finalAmount;

    @Column(name = "used_coupon_id")
    private Long usedCouponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    protected OrderModel() {}

    private OrderModel(Long userId, Long totalAmount, Long discountAmount, Long finalAmount,
                       Long usedCouponId, OrderStatus status) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.usedCouponId = usedCouponId;
        this.status = status;
    }

    public static OrderModel create(Long userId, OrderLines lines) {
        return create(userId, lines, 0L, null);
    }

    public static OrderModel create(Long userId, OrderLines lines, long discountAmount, Long usedCouponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID 는 비어있을 수 없습니다.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.EMPTY_ORDER_ITEMS, "주문 항목이 비어있습니다.");
        }
        if (discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 음수일 수 없습니다.");
        }
        long total = lines.totalAmount();
        if (discountAmount > total) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액을 초과할 수 없습니다.");
        }
        return new OrderModel(userId, total, discountAmount, total - discountAmount, usedCouponId, OrderStatus.CREATED);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalAmount() {
        return finalAmount;
    }

    public Long getUsedCouponId() {
        return usedCouponId;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
