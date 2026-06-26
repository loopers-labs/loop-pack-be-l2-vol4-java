package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;

import java.time.LocalDateTime;

public record CouponInfo(
    Long id,
    String name,
    String type,
    Long value,
    Long minOrderAmount,
    LocalDateTime expiredAt
) {
    public static CouponInfo from(Coupon coupon) {
        return new CouponInfo(
            coupon.getId(),
            coupon.getName(),
            coupon.getType().name(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt()
        );
    }
}
