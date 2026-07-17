package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.MemberCouponInfo;
import com.loopers.coupon.domain.CouponState;
import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;

public record MyCouponResponse(
    Long memberCouponId,
    Long couponId,
    String couponName,
    CouponType type,
    Long value,
    Long minOrderAmount,
    CouponState state,
    ZonedDateTime issuedAt,
    ZonedDateTime usedAt,
    ZonedDateTime expiredAt) {

    public static MyCouponResponse from(MemberCouponInfo info) {
        return new MyCouponResponse(
            info.memberCouponId(),
            info.couponId(),
            info.couponName(),
            info.type(),
            info.value(),
            info.minOrderAmount(),
            info.state(),
            info.issuedAt(),
            info.usedAt(),
            info.expiredAt());
    }
}
