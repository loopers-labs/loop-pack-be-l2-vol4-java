package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponCreateCommand(
    String name,
    CouponType type,
    int value,
    Integer minOrderAmount,
    ZonedDateTime expiredAt
) {
    public CouponModel toDomain() {
        return new CouponModel(name, type, value, minOrderAmount, expiredAt);
    }
}
