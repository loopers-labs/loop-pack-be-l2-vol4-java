package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Table(name = "user_coupon")
public class UserCoupon extends BaseEntity {

    private Long couponTemplateId;

    private Long memberId;

    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    private ZonedDateTime expiredAt;

    private ZonedDateTime usedAt;

    protected UserCoupon() {}

    public UserCoupon(Long memberId, CouponTemplate template) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (template == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿은 필수입니다.");
        }
        this.memberId = memberId;
        this.couponTemplateId = template.getId();
        this.status = CouponStatus.AVAILABLE;
        this.expiredAt = template.getExpiredAt();
    }

    public boolean isAvailable() {
        return status == CouponStatus.AVAILABLE && !ZonedDateTime.now().isAfter(expiredAt);
    }

    public void use() {
        if (!isAvailable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
