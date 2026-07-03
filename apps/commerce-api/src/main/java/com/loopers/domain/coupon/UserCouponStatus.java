package com.loopers.domain.coupon;

public enum UserCouponStatus {
    /** 발급 후 사용 가능 */
    AVAILABLE,
    /** 사용 완료 — 재사용 불가 */
    USED,
    /** 만료 — 사용 불가 */
    EXPIRED,
}
