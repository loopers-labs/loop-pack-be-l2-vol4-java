package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record MyCouponInfo(
        Long userCouponId,
        Long couponId,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        LocalDateTime expiredAt,
        UserCouponStatus status,
        ZonedDateTime usedAt
) {
    public static MyCouponInfo from(UserCouponModel userCoupon, CouponModel coupon, LocalDateTime now) {
        UserCouponStatus status = resolveStatus(userCoupon, coupon, now);
        return new MyCouponInfo(
                userCoupon.getId(),
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountPolicy().type(),
                coupon.getDiscountPolicy().value(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt(),
                status,
                userCoupon.getUsedAt()
        );
    }

    private static UserCouponStatus resolveStatus(UserCouponModel userCoupon, CouponModel coupon, LocalDateTime now) {
        if (userCoupon.isUsed()) {
            return UserCouponStatus.USED;
        }
        if (coupon.isExpired(now)) {
            return UserCouponStatus.EXPIRED;
        }
        return UserCouponStatus.AVAILABLE;
    }
}