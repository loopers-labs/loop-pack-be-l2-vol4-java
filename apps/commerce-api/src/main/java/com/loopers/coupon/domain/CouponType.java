package com.loopers.coupon.domain;

import com.loopers.common.domain.Money;

public enum CouponType {
    FIXED {
        @Override
        long rawDiscount(long value, long orderAmount) {
            return value;
        }
    },
    RATE {
        @Override
        long rawDiscount(long value, long orderAmount) {
            return orderAmount * value / 100;
        }
    };

    abstract long rawDiscount(long value, long orderAmount);

    public Money discount(long value, long orderAmount) {
        return Money.of(Math.min(rawDiscount(value, orderAmount), orderAmount));
    }
}
