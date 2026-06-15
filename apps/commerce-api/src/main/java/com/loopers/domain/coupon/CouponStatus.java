package com.loopers.domain.coupon;

public enum CouponStatus {
    AVAILABLE,  // 사용 가능
    USED,       // 사용 완료 (재사용 불가)
    EXPIRED     // 만료 (조회 시 expiredAt 기준으로 파생되는 표시 상태)
}
