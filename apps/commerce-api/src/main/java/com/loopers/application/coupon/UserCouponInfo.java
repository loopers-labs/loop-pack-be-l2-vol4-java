package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.coupon.vo.CouponMoney;

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
