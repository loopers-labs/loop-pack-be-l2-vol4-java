package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
public class OrderModel extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coupon_id")
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    protected OrderModel() {}

    public OrderModel(Long memberId, Long totalPrice) {
        this(memberId, null, totalPrice, 0L);
    }

    public OrderModel(Long memberId, Long couponId, Long originalAmount, Long discountAmount) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (originalAmount == null || originalAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 가격은 0 이상이어야 합니다.");
        }
        if (discountAmount == null || discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }
        this.memberId = memberId;
        this.couponId = couponId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.totalPrice = originalAmount - discountAmount;
        this.status = OrderStatus.PENDING;
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 취소된 주문입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getOriginalAmount() {
        return originalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }
}
