package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.ZonedDateTime;

public record IssuedCouponInfo(
    Long couponId,
    Long userId,
    Long templateId,
    CouponStatus status,
    ZonedDateTime createdAt
) {
    public static IssuedCouponInfo from(IssuedCoupon coupon) {
        return new IssuedCouponInfo(
            coupon.getId(),
            coupon.getUserId(),
            coupon.getCouponTemplateId(),
            coupon.getStatus(),
            coupon.getCreatedAt()
        );
    }
}
