package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.coupon.MyCouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record MyCouponV1Response(
    Long couponId,
    String name,
    CouponType type,
    long discountValue,
    CouponStatus status,
    ZonedDateTime expiredAt
) {
    public static MyCouponV1Response from(MyCouponInfo info) {
        return new MyCouponV1Response(
            info.couponId(),
            info.name(),
            info.type(),
            info.discountValue(),
            info.status(),
            info.expiredAt()
        );
    }
}
