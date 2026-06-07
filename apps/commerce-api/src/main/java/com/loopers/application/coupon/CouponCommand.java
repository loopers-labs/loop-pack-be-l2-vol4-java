package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponCommand {
    public record CreateTemplate(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt
    ) {}

    public record UpdateTemplate(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt
    ) {}
}
