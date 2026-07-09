package com.loopers.application.outbox;

/**
 * coupon-issue-requests 토픽에 발행되는 메시지 페이로드 (key=couponId).
 * commerce-api 자신이 이 토픽의 Consumer이기도 하므로(application/coupon/CouponIssuePayload),
 * 두 곳이 같은 필드 계약을 각자 정의한다 — catalog/order-events의 producer/consumer 앱 분리 관례와 일관성을 위해 의도적으로 공유 타입을 두지 않았다.
 */
record CouponIssueRequestPayload(String eventId, String eventType, Long requestId, Long userId, Long couponId) {

    static final String ISSUE_REQUESTED = "ISSUE_REQUESTED";
}
