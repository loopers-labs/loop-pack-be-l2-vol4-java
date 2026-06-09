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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "original_price", nullable = false)
    private Long originalPrice;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "final_price", nullable = false)
    private Long finalPrice;

    protected OrderModel() {}

    public OrderModel(Long userId, Long originalPrice, Long discountAmount, Long finalPrice, Long userCouponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저는 필수입니다.");
        }
        if (originalPrice == null || originalPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 0 이상이어야 합니다.");
        }
        if (discountAmount == null || discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }
        if (finalPrice == null || finalPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최종 금액은 0 이상이어야 합니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.userCouponId = userCouponId;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.finalPrice = finalPrice;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public Long getOriginalPrice() {
        return originalPrice;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalPrice() {
        return finalPrice;
    }

    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "PENDING 상태의 주문만 결제할 수 있습니다.");
        }
        this.status = OrderStatus.PAID;
    }
}
