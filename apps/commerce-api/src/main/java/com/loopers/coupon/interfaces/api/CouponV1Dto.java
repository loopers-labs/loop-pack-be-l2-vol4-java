package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.UserCouponInfo;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record UserCouponResponse(
        Long id,
        Long couponTemplateId,
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt,
        UserCouponStatus displayStatus,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {

        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.couponTemplateId(),
                info.name(),
                info.type(),
                info.discountValue(),
                info.minimumOrderAmount(),
                info.expiredAt(),
                info.displayStatus(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }
}
