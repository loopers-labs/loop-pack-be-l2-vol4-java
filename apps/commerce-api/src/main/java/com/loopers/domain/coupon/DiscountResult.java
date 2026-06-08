package com.loopers.domain.coupon;

public record DiscountResult(Long usedCouponId, long amount) {

    public static DiscountResult none() {
        return new DiscountResult(null, 0L);
    }
}
