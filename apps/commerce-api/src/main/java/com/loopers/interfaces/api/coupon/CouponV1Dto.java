package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;

import java.time.LocalDateTime;

public class CouponV1Dto {

    public record UserCouponResponse(
        Long userCouponId,
        Long couponId,
        String couponName,
        String couponType,
        Long discountValue,
        Long minOrderAmount,
        String status,          // AVAILABLE | USED | EXPIRED
        LocalDateTime expiredAt,
        LocalDateTime issuedAt,
        LocalDateTime usedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.userCouponId(),
                info.couponId(),
                info.couponName(),
                info.couponType(),
                info.discountValue(),
                info.minOrderAmount(),
                info.displayStatus(),
                info.expiredAt(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }
}
