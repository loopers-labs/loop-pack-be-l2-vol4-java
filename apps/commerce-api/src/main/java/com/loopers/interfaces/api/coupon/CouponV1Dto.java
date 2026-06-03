package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssuedCouponResponse(
        Long userCouponId,
        Long couponId,
        String couponName,
        String type,
        long value,
        Long minOrderAmount,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        ZonedDateTime expiredAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                info.userCouponId(),
                info.couponId(),
                info.couponName(),
                info.type() == null ? null : info.type().name(),
                info.value(),
                info.minOrderAmount(),
                info.status() == null ? null : info.status().name(),
                info.issuedAt(),
                info.usedAt(),
                info.expiredAt()
            );
        }
    }
}
