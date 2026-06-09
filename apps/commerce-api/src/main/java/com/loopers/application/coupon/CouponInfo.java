package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record CouponInfo(
        Long id,
        String name,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static CouponInfo from(CouponTemplateModel model) {
        return new CouponInfo(
                model.getId(),
                model.getName(),
                model.getDiscountPolicy().type(),
                model.getDiscountPolicy().value(),
                model.getMinOrderAmount(),
                model.getExpiredAt(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
