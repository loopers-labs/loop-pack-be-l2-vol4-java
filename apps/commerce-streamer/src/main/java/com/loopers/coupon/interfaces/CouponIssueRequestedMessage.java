package com.loopers.coupon.interfaces;

import java.time.ZonedDateTime;

/**
 * coupon-issue-requests 토픽에서 소비하는 발급 요청 메시지.
 * producer(commerce-api)의 CouponIssueRequestedMessage 와 필드명이 일치해야 한다.
 */
public record CouponIssueRequestedMessage(
        String requestId,
        Long couponId,
        Long userId,
        ZonedDateTime requestedAt
) {
}
