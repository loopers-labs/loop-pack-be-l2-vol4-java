package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;

public record CreateCouponCommand(
    String name,
    CouponType type,
    long discountValue,
    Long minimumOrderAmount,
    ZonedDateTime expiredAt
) {
}
