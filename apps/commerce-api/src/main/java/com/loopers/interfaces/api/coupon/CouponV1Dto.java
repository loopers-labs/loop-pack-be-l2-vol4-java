package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record UserCouponResponse(
        Long id,
        Long userId,
        Long couponTemplateId,
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt,
        UserCouponStatus status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {

        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.userId(),
                info.couponTemplateId(),
                info.name(),
                info.type(),
                info.discountValue(),
                info.minimumOrderAmount(),
                info.expiredAt(),
                info.status(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }
}
