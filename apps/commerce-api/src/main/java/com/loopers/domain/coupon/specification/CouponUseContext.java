package com.loopers.domain.coupon.specification;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.vo.CouponMoney;

import java.time.ZonedDateTime;

public record CouponUseContext(
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

    public boolean isCouponIssuedToUser() {
        return userCoupon.isIssuedTo(userId);
    }

    public boolean isCouponAvailable() {
        return userCoupon.isAvailable();
    }

    public boolean isCouponApplicableToOrder() {
        return couponTemplate.canApplyTo(orderAmount, now);
    }
}
