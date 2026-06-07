package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;

import java.time.ZonedDateTime;

public record IssuedCouponInfo(
    Long id,
    Long userId,
    Long couponPolicyId,
    CouponType type,
    long discountValue,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    CouponDisplayStatus status,
    ZonedDateTime usedAt,
    ZonedDateTime issuedAt
) {
    public static IssuedCouponInfo from(UserCoupon userCoupon, ZonedDateTime now) {
        return new IssuedCouponInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            userCoupon.getCouponPolicyId(),
            userCoupon.getType(),
            userCoupon.getDiscountValue(),
            userCoupon.getMinOrderAmount(),
            userCoupon.getExpiredAt(),
            userCoupon.displayStatus(now),
            userCoupon.getUsedAt(),
            userCoupon.getCreatedAt()
        );
    }
}
