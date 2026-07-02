package com.loopers.domain.coupon;

/**
 * 선착순 쿠폰 발급 "요청" 의 수명주기 상태.
 * PENDING(접수) 에서 출발해 단 한 번 terminal(ISSUED|REJECTED) 로 확정된다.
 */
public enum CouponIssueRequestStatus {
    PENDING,
    ISSUED,
    REJECTED
}
