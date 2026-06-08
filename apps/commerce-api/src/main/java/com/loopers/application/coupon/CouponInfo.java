package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponInfo(
    Long id,
    String name,
    CouponType type,
    long discountValue,
    Long minimumOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {

    public static CouponInfo from(CouponTemplate coupon) {
        Long minimumOrderAmount = coupon.getMinimumOrderAmount() == null
            ? null
            : coupon.getMinimumOrderAmount().value();
        return new CouponInfo(
            coupon.getId(),
            coupon.getName(),
            coupon.getType(),
            coupon.getDiscountValue().value(),
            minimumOrderAmount,
            coupon.getExpiration().expiredAt(),
            coupon.getCreatedAt(),
            coupon.getUpdatedAt(),
            coupon.getDeletedAt()
        );
    }
}
