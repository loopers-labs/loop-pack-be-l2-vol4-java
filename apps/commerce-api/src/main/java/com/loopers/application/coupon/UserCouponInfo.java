package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCoupon;

import java.time.ZonedDateTime;

public record UserCouponInfo(
    Long id,
    Long userId,
    Long couponId,
    String name,
    String type,
    long value,
    Long minOrderAmount,
    String status,
    ZonedDateTime issuedAt,
    ZonedDateTime usedAt,
    ZonedDateTime expiredAt
) {
    public static UserCouponInfo from(UserCoupon userCoupon, ZonedDateTime now) {
        return new UserCouponInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            userCoupon.getCouponId(),
            userCoupon.getSnapshot().getName(),
            userCoupon.getSnapshot().getType().name(),
            userCoupon.getSnapshot().getValue(),
            userCoupon.getSnapshot().getMinOrderAmount(),
            userCoupon.statusFor(now).name(),
            userCoupon.getIssuedAt(),
            userCoupon.getUsedAt(),
            userCoupon.getExpiredAt()
        );
    }
}
