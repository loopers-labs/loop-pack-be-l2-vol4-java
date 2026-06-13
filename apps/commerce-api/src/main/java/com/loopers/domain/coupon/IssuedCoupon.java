package com.loopers.domain.coupon;

import com.loopers.domain.BaseDomain;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class IssuedCoupon extends BaseDomain {

    private Long couponId;
    private Long userId;
    private CouponStatus status;
    private ZonedDateTime usedAt;
    private ZonedDateTime expiredAt;

    public IssuedCoupon(Long couponId, Long userId, ZonedDateTime expiredAt) {
        validate(couponId, userId);
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponStatus.AVAILABLE;
        this.expiredAt = expiredAt;
    }

    public IssuedCoupon(Long id, Long couponId, Long userId, CouponStatus status, ZonedDateTime usedAt,
                        ZonedDateTime expiredAt, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.usedAt = usedAt;
        this.expiredAt = expiredAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public void use() {
        if (status != CouponStatus.AVAILABLE || ZonedDateTime.now().isAfter(expiredAt)) {
            throw new CoreException(ErrorType.CONFLICT, "사용 불가능한 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public boolean isAvailable() {
        return status == CouponStatus.AVAILABLE && ZonedDateTime.now().isBefore(expiredAt);
    }

    private void validate(Long couponId, Long userId) {
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }
}
