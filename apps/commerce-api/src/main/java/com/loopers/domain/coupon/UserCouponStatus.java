package com.loopers.domain.coupon;

/**
 * AVAILABLE/USED 만 영속화한다. EXPIRED 는 expiredAt 기준 파생 상태 (UserCoupon.statusFor).
 */
public enum UserCouponStatus {
    AVAILABLE, USED, EXPIRED
}
