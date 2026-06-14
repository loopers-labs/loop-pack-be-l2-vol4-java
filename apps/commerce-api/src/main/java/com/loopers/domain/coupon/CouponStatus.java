package com.loopers.domain.coupon;

/**
 * 발급된 쿠폰(UserCoupon)의 상태.
 * AVAILABLE/USED 만 저장되며, EXPIRED 는 조회 시점에 템플릿 만료일과 비교해 계산한다.
 */
public enum CouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}
