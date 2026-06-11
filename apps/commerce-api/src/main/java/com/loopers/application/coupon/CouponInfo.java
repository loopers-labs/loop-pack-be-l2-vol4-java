package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;

public record CouponInfo(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        LocalDateTime expiredAt,
        Integer quantity
) {
    public static CouponInfo from(CouponModel coupon) {
        return new CouponInfo(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountPolicy().type(),
                coupon.getDiscountPolicy().value(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt(),
                coupon.getQuantity()
        );
    }
}