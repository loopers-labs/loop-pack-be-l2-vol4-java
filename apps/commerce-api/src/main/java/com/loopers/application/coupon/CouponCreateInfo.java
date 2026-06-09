package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;

public record CouponCreateInfo(Long couponId) {

    public static CouponCreateInfo from(CouponModel coupon) {
        return new CouponCreateInfo(coupon.getId());
    }
}
