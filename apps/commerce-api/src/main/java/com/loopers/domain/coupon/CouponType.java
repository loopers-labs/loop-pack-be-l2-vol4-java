package com.loopers.domain.coupon;

/**
 * 쿠폰 종류.
 * - FIXED: 정액 할인 (value 원 차감)
 * - RATE:  정률 할인 (orderTotal × value/100)
 */
public enum CouponType {
    FIXED,
    RATE
}
