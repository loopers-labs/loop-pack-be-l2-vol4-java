package com.loopers.domain.coupon;

import com.loopers.domain.coupon.specification.CouponUseAttempt;
import com.loopers.domain.coupon.vo.CouponMoney;

import java.time.ZonedDateTime;

public record CouponUseCommand(
    Long userId,
    Long userCouponId,
    CouponMoney orderAmount,
    ZonedDateTime usedAt
) {

    public static CouponUseCommand forOrder(
        Long userId,
        Long userCouponId,
        long orderAmount,
        ZonedDateTime usedAt
    ) {
        return new CouponUseCommand(userId, userCouponId, CouponMoney.of(orderAmount), usedAt);
    }

    public boolean hasCoupon() {
        return userCouponId != null;
    }

    public CouponUseAttempt toAttempt(UserCoupon userCoupon) {
        return new CouponUseAttempt(userCoupon, userId, orderAmount, usedAt);
    }
}
