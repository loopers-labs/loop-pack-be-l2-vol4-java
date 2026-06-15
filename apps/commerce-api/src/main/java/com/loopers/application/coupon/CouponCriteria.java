package com.loopers.application.coupon;

import com.loopers.domain.coupon.DiscountType;

public final class CouponCriteria {

    private CouponCriteria() {
    }

    public record RegisterTemplate(String name, DiscountType discountType, long discountValue,
                                   long minOrderAmount, int validDays) {
    }

    public record ModifyTemplate(Long id, String name, DiscountType discountType, long discountValue,
                                 long minOrderAmount, int validDays) {
    }
}
