package com.loopers.coupon.interfaces.api.admin;

import com.loopers.coupon.application.CouponInfo;
import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;

public record CouponResponse(
    Long id,
    String name,
    CouponType type,
    Long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt) {

    public static CouponResponse from(CouponInfo info) {
        return new CouponResponse(
            info.id(), info.name(), info.type(), info.value(), info.minOrderAmount(), info.expiredAt());
    }
}
