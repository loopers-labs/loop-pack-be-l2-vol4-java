package com.loopers.coupon.domain;

public enum CouponStatus {
    AVAILABLE,
    USED,
    EXPIRED; // EXPIRED 는 저장하지 않고 조회 시점에 만료 시각으로 계산하는 파생 상태
}
