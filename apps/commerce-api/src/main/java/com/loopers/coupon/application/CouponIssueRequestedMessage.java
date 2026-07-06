package com.loopers.coupon.application;

import java.time.ZonedDateTime;

/**
 * coupon-issue-requests 토픽으로 발행되는 선착순 발급 요청 메시지.
 * Consumer(commerce-streamer)가 이 필드명 기준으로 역직렬화한다.
 */
public record CouponIssueRequestedMessage(
        String requestId,
        Long couponId,
        Long userId,
        ZonedDateTime requestedAt
) {
}
