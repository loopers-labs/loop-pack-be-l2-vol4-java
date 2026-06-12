package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponAdminInfo(
    Long id,
    String name,
    CouponType type,
    int value,
    int minOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt
) {
    public static CouponAdminInfo from(CouponModel coupon) {
        return new CouponAdminInfo(
            coupon.getId(),
            coupon.getName(),
            coupon.getType(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt(),
            coupon.getCreatedAt()
        );
    }
}
