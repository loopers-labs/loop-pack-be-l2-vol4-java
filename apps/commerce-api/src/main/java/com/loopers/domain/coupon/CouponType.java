package com.loopers.domain.coupon;

/**
 * 쿠폰 할인 방식.
 * FIXED: 정액(원 단위 할인), RATE: 정률(퍼센트 할인)
 */
public enum CouponType {
    FIXED,
    RATE,
}
