package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;

public record CouponInfo(
    Long id,
    String name,
    CouponType type,
    Long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt) {

    public static CouponInfo from(Coupon coupon) {
        return new CouponInfo(
            coupon.getId(),
            coupon.getName(),
            coupon.getType(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt());
    }
}
