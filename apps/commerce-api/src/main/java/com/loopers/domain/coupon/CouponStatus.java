package com.loopers.domain.coupon;

/**
 * 쿠폰 상태.
 * <p>
 * {@code AVAILABLE}/{@code USED} 두 값만 저장하고, {@code EXPIRED} 는 저장하지 않는다 —
 * "AVAILABLE 이면서 만료일이 지난" 쿠폰을 조회 시점에 만료로 파생한다
 * ({@link UserCoupon#displayStatus}).
 */
public enum CouponStatus {
    AVAILABLE,
    USED,
    EXPIRED
}
