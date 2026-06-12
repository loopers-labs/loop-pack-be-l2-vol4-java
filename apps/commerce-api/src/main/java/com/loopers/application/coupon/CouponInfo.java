package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import java.time.ZonedDateTime;

public record CouponInfo(
    Long userCouponId,
    Long couponId,
    String name,
    CouponType type,
    int value,
    int minOrderAmount,
    ZonedDateTime expiredAt,
    CouponStatus status
) {
    public static CouponInfo from(UserCouponModel userCoupon, CouponModel coupon) {
        CouponStatus effectiveStatus = resolveStatus(userCoupon, coupon);
        return new CouponInfo(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            coupon.getType(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt(),
            effectiveStatus
        );
    }

    private static CouponStatus resolveStatus(UserCouponModel userCoupon, CouponModel coupon) {
        if (userCoupon.getStatus() == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        if (ZonedDateTime.now().isAfter(coupon.getExpiredAt())) {
            return CouponStatus.EXPIRED;
        }
        return CouponStatus.AVAILABLE;
    }
}
