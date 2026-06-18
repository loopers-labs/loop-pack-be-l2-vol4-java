package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record Response(
        Long id,
        Long couponPolicyId,
        CouponType type,
        long discountValue,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponDisplayStatus status,
        ZonedDateTime usedAt
    ) {
        public static Response from(CouponInfo info) {
            return new Response(
                info.id(),
                info.couponPolicyId(),
                info.type(),
                info.discountValue(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.status(),
                info.usedAt()
            );
        }
    }
}
