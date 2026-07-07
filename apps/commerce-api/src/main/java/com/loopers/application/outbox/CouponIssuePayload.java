package com.loopers.application.outbox;

public record CouponIssuePayload(String requestId, Long couponId, Long userId, String occurredAt) {}
