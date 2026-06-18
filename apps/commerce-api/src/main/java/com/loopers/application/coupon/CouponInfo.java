package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;
import java.util.UUID;

public record CouponInfo(
    UUID id,
    String name,
    CouponType type,
    Long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt,
    ZonedDateTime deletedAt
) {

    public static CouponInfo from(CouponTemplateModel model) {
        return new CouponInfo(
            model.getId(),
            model.getName(),
            model.getType(),
            model.getValue(),
            model.getMinOrderAmount(),
            model.getExpiredAt(),
            model.getCreatedAt(),
            model.getDeletedAt()
        );
    }
}
