package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;

import java.time.ZonedDateTime;

public record CouponInfo(
    Long id,
    Long couponPolicyId,
    CouponType type,
    long discountValue,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    CouponDisplayStatus status,
    ZonedDateTime usedAt
) {
    public static CouponInfo from(UserCoupon userCoupon, ZonedDateTime now) {
        return new CouponInfo(
            userCoupon.getId(),
            userCoupon.getCouponPolicyId(),
            userCoupon.getType(),
            userCoupon.getDiscountValue(),
            userCoupon.getMinOrderAmount(),
            userCoupon.getExpiredAt(),
            userCoupon.displayStatus(now),
            userCoupon.getUsedAt()
        );
    }
}
