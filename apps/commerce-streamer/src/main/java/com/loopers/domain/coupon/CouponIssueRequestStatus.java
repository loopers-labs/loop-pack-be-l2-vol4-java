package com.loopers.domain.coupon;

/**
 * 발급 요청의 처리 상태. streamer 는 처리 결과에 따라 PENDING → ISSUED|REJECTED 로 확정한다.
 * (api 의 동명 enum 과 같은 이름 집합 — 같은 coupon_issue_request.status VARCHAR 를 공유한다)
 */
public enum CouponIssueRequestStatus {
    PENDING,
    ISSUED,
    REJECTED
}
