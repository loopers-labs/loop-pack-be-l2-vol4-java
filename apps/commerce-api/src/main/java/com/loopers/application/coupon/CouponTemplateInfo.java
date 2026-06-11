package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateEntity;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponTemplateInfo(
        Long templateId,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static CouponTemplateInfo from(CouponTemplateEntity template) {
        return new CouponTemplateInfo(
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getExpiredAt(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
