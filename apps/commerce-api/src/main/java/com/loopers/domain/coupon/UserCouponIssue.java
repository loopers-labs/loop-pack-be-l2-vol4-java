package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.UserCouponStatus;

import java.time.ZonedDateTime;

public record UserCouponIssue(
        Long id,
        Long userId,
        Long couponId,
        UserCouponStatus status,
        ZonedDateTime usedAt
) {}
