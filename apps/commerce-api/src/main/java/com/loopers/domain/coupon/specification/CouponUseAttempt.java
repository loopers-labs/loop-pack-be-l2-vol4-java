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

    public boolean hasIssuedCoupon() {
        return userCoupon != null;
    }

    public boolean hasCouponTemplate() {
        return couponTemplate != null;
    }

    public boolean isIssuedToUser() {
        return userCoupon.isIssuedTo(userId);
    }

    public boolean isAvailable() {
        return userCoupon.isAvailable();
    }

    public boolean isApplicableToOrder() {
        return couponTemplate.canApplyTo(orderAmount, now);
    }
}
