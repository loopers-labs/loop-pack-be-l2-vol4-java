package com.loopers.interfaces.consumer;

/**
 * coupon-issue-requests 토픽에서 소비하는 발급 요청 메시지.
 * commerce-api의 CouponIssueRequestedEvent와 같은 JSON 구조를 매핑한다.
 */
public record CouponIssueRequestMessage(
    String requestId,
    Long couponId,
    Long userId
) {
}
