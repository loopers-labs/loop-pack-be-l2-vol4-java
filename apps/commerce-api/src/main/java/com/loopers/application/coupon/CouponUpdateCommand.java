package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponUpdateCommand(
    String name,
    CouponType type,
    int value,
    Integer minOrderAmount,
    ZonedDateTime expiredAt
) {}
