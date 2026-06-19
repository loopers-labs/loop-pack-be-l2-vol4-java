package com.loopers.coupon.domain;

import com.loopers.coupon.domain.vo.CouponMoney;

import java.time.ZonedDateTime;

public record CouponUse(
    Long userId,
    Long userCouponId,
    CouponMoney orderAmount,
    ZonedDateTime usedAt
) {

    public static CouponUse create(
        Long userId,
        Long userCouponId,
        long orderAmount,
        ZonedDateTime usedAt
    ) {
        return new CouponUse(userId, userCouponId, CouponMoney.of(orderAmount), usedAt);
    }

    public boolean hasCoupon() {
        return userCouponId != null;
    }
}
