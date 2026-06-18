package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.MyCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record MyCouponResponse(
            Long userCouponId,
            Long couponId,
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            LocalDateTime expiredAt,
            UserCouponStatus status,
            ZonedDateTime usedAt
    ) {
        public static MyCouponResponse from(MyCouponInfo info) {
            return new MyCouponResponse(
                    info.userCouponId(),
                    info.couponId(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.status(),
                    info.usedAt()
            );
        }
    }
}