package com.loopers.domain.coupon.specification;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.vo.CouponMoney;

import java.time.ZonedDateTime;

public record CouponUseAttempt(
    UserCoupon userCoupon,
    Long userId,
    CouponMoney orderAmount,
    ZonedDateTime now
) {

    public static CouponUseAttempt attempt(
        UserCoupon userCoupon,
        Long userId,
        CouponMoney orderAmount,
        ZonedDateTime now
    ) {
        return new CouponUseAttempt(userCoupon, userId, orderAmount, now);
    }

    public boolean canUse() {
        return userCoupon.canBeUsedBy(userId)
            && userCoupon.canApplyTo(orderAmount, now);
    }

    public void confirmUsable() {
        userCoupon.confirmUsableBy(userId);
        userCoupon.confirmApplicableTo(orderAmount, now);
    }
}
