package com.loopers.domain.coupon.event;

import java.time.ZonedDateTime;

public record CouponIssueRequested(String requestId, Long couponId, Long userId, ZonedDateTime occurredAt) {}
