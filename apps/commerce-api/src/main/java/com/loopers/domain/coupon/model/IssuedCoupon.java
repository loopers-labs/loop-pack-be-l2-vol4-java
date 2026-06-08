package com.loopers.domain.coupon.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(
    name = "issued_coupons",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "coupon_template_id"})
)
@Getter
public class IssuedCoupon extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    protected IssuedCoupon() {}

    private IssuedCoupon(Long memberId, Long couponTemplateId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
        this.memberId = memberId;
        this.couponTemplateId = couponTemplateId;
        this.status = CouponStatus.AVAILABLE;
    }

    public static IssuedCoupon create(Long memberId, Long couponTemplateId) {
        return new IssuedCoupon(memberId, couponTemplateId);
    }

    public void use() {
        if (status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 불가능한 쿠폰입니다. 현재 상태: " + status);
        }
        this.status = CouponStatus.USED;
    }

    public void expire() {
        this.status = CouponStatus.EXPIRED;
    }
}
