package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCouponModel;

import java.time.ZonedDateTime;

public record IssuedCouponInfo(
        Long id,
        Long couponTemplateId,
        Long userId,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static IssuedCouponInfo from(IssuedCouponModel model) {
        return new IssuedCouponInfo(
                model.getId(),
                model.getCouponTemplateId(),
                model.getUserId(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
