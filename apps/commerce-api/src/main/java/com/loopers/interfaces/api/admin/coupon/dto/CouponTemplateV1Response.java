package com.loopers.interfaces.api.admin.coupon.dto;

import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponTemplateV1Response(
    Long id,
    String name,
    CouponType type,
    long discountValue,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt
) {
    public static CouponTemplateV1Response from(CouponTemplateInfo info) {
        return new CouponTemplateV1Response(
            info.id(),
            info.name(),
            info.type(),
            info.discountValue(),
            info.minOrderAmount(),
            info.expiredAt(),
            info.createdAt()
        );
    }
}
