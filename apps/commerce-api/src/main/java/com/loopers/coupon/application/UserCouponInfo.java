package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponStatus;
import com.loopers.coupon.domain.vo.CouponMoney;

import java.time.ZonedDateTime;

public record UserCouponInfo(
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

    public static UserCouponInfo from(UserCoupon userCoupon) {
        return new UserCouponInfo(
            userCoupon.getId(),
            userCoupon.getCouponTemplateId(),
            userCoupon.getName(),
            userCoupon.getType(),
            userCoupon.getDiscountValue().value(),
            minimumOrderAmount(userCoupon),
            userCoupon.getExpiration().expiredAt(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt(),
            userCoupon.getUsedAt()
        );
    }

    private static Long minimumOrderAmount(UserCoupon userCoupon) {
        CouponMoney minimumOrderAmount = userCoupon.getMinimumOrderAmount();
        return minimumOrderAmount == null ? null : minimumOrderAmount.value();
    }
}
