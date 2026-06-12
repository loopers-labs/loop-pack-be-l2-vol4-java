package com.loopers.domain.coupon;

public record CouponUseResult(
    Long issuedCouponId,
    Long couponId,
    Long discountAmount
) {
}
