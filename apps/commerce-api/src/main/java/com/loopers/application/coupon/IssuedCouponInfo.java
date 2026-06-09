package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCouponModel;

import java.time.ZonedDateTime;

public record IssuedCouponInfo(
        Long id,
        Long couponTemplateId,
        Long userId,
        CouponStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static IssuedCouponInfo from(IssuedCouponModel model, boolean templateExpired) {
        return new IssuedCouponInfo(
                model.getId(),
                model.getCouponTemplateId(),
                model.getUserId(),
                model.resolveStatus(templateExpired),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
