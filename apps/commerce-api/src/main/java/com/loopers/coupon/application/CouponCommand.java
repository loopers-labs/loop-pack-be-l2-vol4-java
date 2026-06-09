package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;

public class CouponCommand {

    public record Create(
            String name,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
    }

    public record Update(
            Long couponId,
            String name,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
    }
}
