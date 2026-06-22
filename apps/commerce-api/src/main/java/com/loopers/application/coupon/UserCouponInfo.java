package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;

import java.time.LocalDateTime;

public record UserCouponInfo(
    Long id,
    String templateName,
    LocalDateTime expiredAt,
    CouponStatus status
) {}
