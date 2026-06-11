package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponAdminDto {
    public record RegisterTemplateRequest(
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            LocalDateTime expiredAt
    ) {}

    public record UpdateTemplateRequest(
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            LocalDateTime expiredAt
    ) {}

    public record TemplateResponse(
            Long id,
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            LocalDateTime expiredAt
    ) {}

    public record IssueResponse(
            Long id,
            Long userId,
            Long couponTemplateId,
            String status,
            java.time.ZonedDateTime createdAt
    ) {}
}
