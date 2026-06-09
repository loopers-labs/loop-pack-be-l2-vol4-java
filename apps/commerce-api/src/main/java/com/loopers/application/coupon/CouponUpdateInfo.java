package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;

public record CouponUpdateInfo(Long couponId) {

    public static CouponUpdateInfo from(CouponModel coupon) {
        return new CouponUpdateInfo(coupon.getId());
    }
}
