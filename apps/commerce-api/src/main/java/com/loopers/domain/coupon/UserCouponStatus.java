package com.loopers.domain.coupon;

/**
 * 발급 쿠폰의 상태 (01 §3.3). 저장되지 않고 used_at + 템플릿 expired_at으로 조회 시점에 파생된다.
 */
public enum UserCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED
}
