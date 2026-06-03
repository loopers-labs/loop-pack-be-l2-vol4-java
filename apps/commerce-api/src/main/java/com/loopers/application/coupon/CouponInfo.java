package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

/** 쿠폰 템플릿 응답용 (Admin). */
public record CouponInfo(
        Long id,
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        boolean active
) {
    public static CouponInfo from(CouponModel coupon) {
        return new CouponInfo(
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt(),
                coupon.isActive()
        );
    }
}
