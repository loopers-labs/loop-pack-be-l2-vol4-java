package com.loopers.domain.coupon;

public enum CouponType {
    FIXED {
        @Override
        long rawDiscount(long value, long originalAmount) {
            return value;
        }
    },
    RATE {
        @Override
        long rawDiscount(long value, long originalAmount) {
            return originalAmount * value / 100; // 정수 나눗셈 = 버림(floor)
        }
    };

    abstract long rawDiscount(long value, long originalAmount);
}
