package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponInfo(
    Long id,
    String name,
    CouponType type,
    int value,
    Integer minOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt
) {
    public static CouponInfo from(CouponModel model) {
        return new CouponInfo(
            model.getId(),
            model.getName(),
            model.getType(),
            model.getValue(),
            model.getMinOrderAmount(),
            model.getExpiredAt(),
            model.getCreatedAt()
        );
    }
}
