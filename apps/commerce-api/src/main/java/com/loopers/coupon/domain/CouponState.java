package com.loopers.coupon.domain;

/** 발급된 회원 쿠폰의 상태. 사용 가능(AVAILABLE) / 사용 완료(USED) / 만료(EXPIRED). */
public enum CouponState {
    AVAILABLE,
    USED,
    EXPIRED
}
