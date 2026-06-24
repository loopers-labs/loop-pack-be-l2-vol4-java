package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "issued_coupon")
public class IssuedCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected IssuedCoupon() {}

    public IssuedCoupon(Long userId, Long couponTemplateId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 비어있을 수 없습니다.");
        }
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.status = CouponStatus.AVAILABLE;
    }

    public void use() {
        if (this.status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용했거나 사용할 수 없는 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
    }

    /** 결제 실패 보상 시 사용 취소(USED → AVAILABLE). 이미 AVAILABLE이면 멱등 no-op. */
    public void cancel() {
        if (this.status == CouponStatus.USED) {
            this.status = CouponStatus.AVAILABLE;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public CouponStatus displayStatus(CouponTemplate template, ZonedDateTime at) {
        if (this.status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        return template.isExpired(at) ? CouponStatus.EXPIRED : CouponStatus.AVAILABLE;
    }
}
