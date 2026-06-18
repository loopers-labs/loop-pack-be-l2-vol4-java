package com.loopers.domain.coupon;

public enum CouponType {
    FIXED {
        @Override
        public long discount(long value, long orderAmount) {
            return Math.min(value, orderAmount);
        }
    },
    RATE {
        @Override
        public long discount(long value, long orderAmount) {
            return orderAmount * value / 100;
        }
    };

    public abstract long discount(long value, long orderAmount);
}