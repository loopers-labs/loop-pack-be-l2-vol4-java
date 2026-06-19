package com.loopers.domain.coupon;

/**
 * 발급된 쿠폰(UserCoupon)의 상태.
 * AVAILABLE 에서만 사용 가능하며, 한 번 USED 가 되면 재사용 불가.
 * EXPIRED 는 별도 상태로 저장하지 않고 응답 시 Coupon.expiredAt 으로 계산한다.
 */
public enum CouponStatus {
    AVAILABLE,
    USED
}
