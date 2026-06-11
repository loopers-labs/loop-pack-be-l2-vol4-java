package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponV1Dto {
    public record UserCouponResponse(
            Long id,
            Long couponTemplateId,
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            String status,
            LocalDateTime expiredAt
    ) {}
}
