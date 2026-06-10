package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;

import java.time.ZonedDateTime;

public record CouponInfo(
        Long id,
        String name,
        String type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
) {
    public static CouponInfo from(CouponModel coupon) {
        return new CouponInfo(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscount().getType().getDescription(),
                coupon.getDiscount().getValue(),
                coupon.getDiscount().getMinOrderAmount(),
                coupon.getExpiry().getExpiredAt()
        );
    }
}
