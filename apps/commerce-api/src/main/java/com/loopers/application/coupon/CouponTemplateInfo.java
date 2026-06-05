package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponTemplateInfo(
    Long id,
    String name,
    CouponType type,
    long discountValue,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt
) {
    public static CouponTemplateInfo from(CouponTemplate template) {
        return new CouponTemplateInfo(
            template.getId(),
            template.getName(),
            template.getType(),
            template.getDiscountValue(),
            template.getMinOrderAmount() == null ? null : template.getMinOrderAmount().value(),
            template.getExpiredAt(),
            template.getCreatedAt()
        );
    }
}
