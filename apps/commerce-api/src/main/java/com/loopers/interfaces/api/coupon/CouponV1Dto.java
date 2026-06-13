package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record MyCouponResponse(
            Long issuedCouponId,
            Long couponId,
            ZonedDateTime expiredAt,
            CouponStatus status
    ) {
        public static MyCouponResponse from(CouponInfo.MyCoupon info) {
            return new MyCouponResponse(
                info.issuedCouponId(), info.couponId(),
                info.expiredAt(), info.status()
            );
        }
    }
}
