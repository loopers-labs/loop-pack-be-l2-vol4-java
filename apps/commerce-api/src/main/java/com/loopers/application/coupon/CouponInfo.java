package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;

public record CouponInfo(Long couponId, Long templateId, CouponStatus status) {
    public static CouponInfo from(IssuedCoupon coupon) {
        return new CouponInfo(coupon.getId(), coupon.getCouponTemplateId(), coupon.getStatus());
    }
}
