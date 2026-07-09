package com.loopers.application.coupon;

/**
 * coupon-issue-requests 토픽 메시지의 Consumer 측 계약.
 * application/outbox/CouponIssueRequestPayload(Producer 측)와 동일한 필드 스키마를 독립적으로 정의한다.
 */
record CouponIssuePayload(String eventId, String eventType, Long requestId, Long userId, Long couponId) {

    static final String ISSUE_REQUESTED = "ISSUE_REQUESTED";
}
