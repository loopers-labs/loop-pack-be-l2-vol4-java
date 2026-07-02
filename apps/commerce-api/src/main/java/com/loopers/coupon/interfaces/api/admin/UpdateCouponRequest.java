package com.loopers.coupon.interfaces.api.admin;

import com.loopers.coupon.domain.CouponType;

import java.time.LocalDateTime;

public record UpdateCouponRequest(
    String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {}
