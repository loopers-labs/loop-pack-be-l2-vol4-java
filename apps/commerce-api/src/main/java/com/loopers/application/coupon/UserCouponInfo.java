package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;

public record UserCouponInfo(
    Long userCouponId,
    String name,
    DiscountType discountType,
    Integer discountValue,
    Integer minOrderAmount,
    ZonedDateTime expiredAt,
    UserCouponStatus status
) {

    public static UserCouponInfo of(UserCouponModel userCoupon, ZonedDateTime now) {
        return new UserCouponInfo(
            userCoupon.getId(),
            userCoupon.getName(),
            userCoupon.getDiscountType(),
            userCoupon.getDiscountValue(),
            userCoupon.getMinOrderAmount(),
            userCoupon.getExpiredAt(),
            userCoupon.getStatus(now)
        );
    }
}
