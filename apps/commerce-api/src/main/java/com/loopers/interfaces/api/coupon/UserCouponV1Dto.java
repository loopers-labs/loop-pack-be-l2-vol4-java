package com.loopers.interfaces.api.coupon;

import java.time.ZonedDateTime;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponStatus;

public class UserCouponV1Dto {

    public record MyCouponResponse(
        Long userCouponId,
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        UserCouponStatus status
    ) {

        public static MyCouponResponse from(UserCouponInfo userCouponInfo) {
            return new MyCouponResponse(
                userCouponInfo.userCouponId(),
                userCouponInfo.name(),
                userCouponInfo.discountType(),
                userCouponInfo.discountValue(),
                userCouponInfo.minOrderAmount(),
                userCouponInfo.expiredAt(),
                userCouponInfo.status()
            );
        }
    }
}
