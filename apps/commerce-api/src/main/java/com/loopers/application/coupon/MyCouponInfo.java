package com.loopers.application.coupon;

import com.loopers.domain.coupon.model.CouponStatus;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.CouponType;
import com.loopers.domain.coupon.model.IssuedCoupon;

import java.time.ZonedDateTime;

public record MyCouponInfo(
    Long issuedCouponId,
    String couponName,
    CouponType type,
    Long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    CouponStatus status
) {
    public static MyCouponInfo of(IssuedCoupon issued, CouponTemplate template) {
        return new MyCouponInfo(
            issued.getId(),
            template.getName(),
            template.getType(),
            template.getValue(),
            template.getMinOrderAmount(),
            template.getExpiredAt(),
            issued.getStatus()
        );
    }
}
