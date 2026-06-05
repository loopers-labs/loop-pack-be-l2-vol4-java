package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.ZonedDateTime;

public record MyCouponInfo(
    Long couponId,
    Long templateId,
    String name,
    CouponType type,
    long discountValue,
    CouponStatus status,
    ZonedDateTime expiredAt
) {
    public static MyCouponInfo from(IssuedCoupon issued, CouponTemplate template, ZonedDateTime at) {
        return new MyCouponInfo(
            issued.getId(),
            template.getId(),
            template.getName(),
            template.getType(),
            template.getDiscountValue(),
            issued.displayStatus(template, at),
            template.getExpiredAt()
        );
    }
}
