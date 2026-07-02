package com.loopers.domain.coupon;

public enum CouponIssueRequestStatus {
    PENDING,   // 접수됨, Consumer 처리 대기
    ISSUED,    // 발급 완료
    REJECTED,  // 거절 (수량 소진, 중복 발급 등)
}
