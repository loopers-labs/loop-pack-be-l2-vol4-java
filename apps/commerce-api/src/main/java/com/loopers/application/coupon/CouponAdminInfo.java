package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponAdminInfo(
    Long id,
    String name,
    CouponType type,
    long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {
    public static CouponAdminInfo from(CouponPolicy policy) {
        return new CouponAdminInfo(
            policy.getId(),
            policy.getName(),
            policy.getType(),
            policy.getValue(),
            policy.getMinOrderAmount(),
            policy.getExpiredAt(),
            policy.getCreatedAt(),
            policy.getUpdatedAt(),
            policy.getDeletedAt()
        );
    }
}
