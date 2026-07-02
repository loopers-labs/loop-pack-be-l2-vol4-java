package com.loopers.tddstudy.messaging;

public record CouponIssueRequested(
        String requestId,
        Long couponId,
        Long userId,
        long occurredAt
) {
    public static final String TOPIC = "coupon-issue-requests";
}
