package com.loopers.application.coupon;

public record CouponIssueRequestMessage(String requestId, Long couponId, Long userId, String occurredAt) {}
