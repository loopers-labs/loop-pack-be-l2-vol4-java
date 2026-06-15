package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record CouponResponse(
            Long userCouponId,
            Long couponId,
            CouponStatus status,
            ZonedDateTime expiredAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                    info.userCouponId(), info.couponId(), info.status(), info.expiredAt());
        }
    }
}
