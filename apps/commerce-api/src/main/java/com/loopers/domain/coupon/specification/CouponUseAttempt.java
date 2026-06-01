package com.loopers.domain.coupon.specification;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.vo.CouponMoney;

import java.time.ZonedDateTime;

public record CouponUseAttempt(
    UserCoupon userCoupon,
    CouponTemplate couponTemplate,
    Long userId,
    CouponMoney orderAmount,
    ZonedDateTime now
) {

    public boolean canUse() {
        return userCoupon.canBeUsedBy(userId)
            && couponTemplate.canApplyTo(orderAmount, now);
    }

    public void confirmUsable() {
        userCoupon.confirmUsableBy(userId);
        couponTemplate.confirmApplicableTo(orderAmount, now);
    }
}
