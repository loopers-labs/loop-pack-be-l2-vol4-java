package com.loopers.domain.order.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "orders")
@Getter
public class Order extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    protected Order() {}

    private Order(Long memberId, Long originalAmount, Long discountAmount, Long issuedCouponId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (originalAmount == null || originalAmount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 0보다 커야 합니다.");
        }
        if (discountAmount == null || discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }
        this.memberId = memberId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = originalAmount - discountAmount;
        this.issuedCouponId = issuedCouponId;
    }

    public static Order create(Long memberId, Long originalAmount, Long discountAmount, Long issuedCouponId) {
        return new Order(memberId, originalAmount, discountAmount, issuedCouponId);
    }
}
