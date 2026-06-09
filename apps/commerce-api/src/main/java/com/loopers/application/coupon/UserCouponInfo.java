package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public record UserCouponInfo(
    Long userCouponId,
    Long couponId,
    String couponName,
    CouponType discountType,
    Long discountValue,
    UserCouponStatus status,
    ZonedDateTime issuedAt
) {
    public static UserCouponInfo from(UserCouponModel userCoupon, CouponModel coupon) {
        return new UserCouponInfo(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            coupon.getDiscountType(),
            coupon.getDiscountValue(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt()
        );
    }
}
