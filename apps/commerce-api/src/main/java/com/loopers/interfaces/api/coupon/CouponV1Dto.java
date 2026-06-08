package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    /** 대고객 - 내 쿠폰/발급 응답. */
    public record UserCouponResponse(
        Long id,
        Long couponId,
        String name,
        String type,
        long value,
        Long minOrderAmount,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.couponId(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.status(),
                info.expiredAt(),
                info.usedAt()
            );
        }
    }
}
