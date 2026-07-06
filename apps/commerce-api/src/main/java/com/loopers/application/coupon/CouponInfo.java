package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;

import java.time.ZonedDateTime;

/** 쿠폰 템플릿 정보 DTO (어드민 화면용). */
public record CouponInfo(
    Long id,
    String name,
    String type,
    long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    Integer totalQuantity,
    long issuedCount
) {
    public static CouponInfo from(CouponModel coupon) {
        return new CouponInfo(
            coupon.getId(),
            coupon.getName(),
            coupon.getType().name(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt(),
            coupon.getTotalQuantity(),
            coupon.getIssuedCount()
        );
    }
}
