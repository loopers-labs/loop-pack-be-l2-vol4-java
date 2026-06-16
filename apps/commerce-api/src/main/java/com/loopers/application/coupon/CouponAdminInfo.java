package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;

public record CouponAdminInfo(
    Long couponId,
    String name,
    DiscountType discountType,
    Integer discountValue,
    Integer minOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {

    public static CouponAdminInfo from(CouponModel coupon) {
        return new CouponAdminInfo(
            coupon.getId(),
            coupon.getName().value(),
            coupon.getType(),
            coupon.getDiscountValue(),
            coupon.getMinOrderAmount().value(),
            coupon.getExpiredAt().value(),
            coupon.getCreatedAt(),
            coupon.getUpdatedAt()
        );
    }
}
