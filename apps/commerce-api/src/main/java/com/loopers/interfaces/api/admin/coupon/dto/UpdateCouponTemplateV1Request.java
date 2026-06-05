package com.loopers.interfaces.api.admin.coupon.dto;

import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateCouponTemplateV1Request(
    @NotBlank String name,
    @NotNull CouponType type,
    long value,
    Long minOrderAmount,
    @NotNull LocalDateTime expiredAt
) {
}
