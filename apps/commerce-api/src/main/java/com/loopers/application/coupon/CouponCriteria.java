package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;

public final class CouponCriteria {

    private CouponCriteria() {}

    public record CreateTemplate(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    public record UpdateTemplate(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {}
}
