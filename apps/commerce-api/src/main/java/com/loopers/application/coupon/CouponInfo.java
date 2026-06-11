package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponEntity;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateEntity;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponInfo(
        Long couponId,
        String templateName,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponStatus status
) {
    public static CouponInfo from(CouponEntity coupon, CouponTemplateEntity template) {
        return new CouponInfo(
                coupon.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getExpiredAt(),
                coupon.resolveStatus(template.getExpiredAt())
        );
    }
}
