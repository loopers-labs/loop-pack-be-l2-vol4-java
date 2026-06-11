package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record UpdateCouponCommand(
    Long couponId,
    String name,
    CouponType type,
    long discountValue,
    Long minimumOrderAmount,
    ZonedDateTime expiredAt
) {
}
