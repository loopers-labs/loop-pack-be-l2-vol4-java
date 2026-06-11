package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssueCouponResponse(
            Long couponId,
            String templateName,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            CouponStatus status
    ) {
        public static IssueCouponResponse from(CouponInfo info) {
            return new IssueCouponResponse(
                    info.couponId(),
                    info.templateName(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.status()
            );
        }
    }

    public record MyCouponResponse(
            Long couponId,
            String templateName,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            CouponStatus status
    ) {
        public static MyCouponResponse from(CouponInfo info) {
            return new MyCouponResponse(
                    info.couponId(),
                    info.templateName(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.status()
            );
        }
    }
}
