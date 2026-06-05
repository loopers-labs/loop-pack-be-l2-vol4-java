package com.loopers.interfaces.api.admin.coupon.dto;

import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.CouponStatus;

import java.time.ZonedDateTime;

public record IssuedCouponV1Response(
    Long couponId,
    Long userId,
    Long templateId,
    CouponStatus status,
    ZonedDateTime createdAt
) {
    public static IssuedCouponV1Response from(IssuedCouponInfo info) {
        return new IssuedCouponV1Response(
            info.couponId(),
            info.userId(),
            info.templateId(),
            info.status(),
            info.createdAt()
        );
    }
}
