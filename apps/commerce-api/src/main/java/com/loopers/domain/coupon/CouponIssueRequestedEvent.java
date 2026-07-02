package com.loopers.domain.coupon;

import java.time.ZonedDateTime;
import java.util.UUID;

public record CouponIssueRequestedEvent(
        String eventId,
        String requestId,
        Long couponId,
        Long userId,
        ZonedDateTime occurredAt
) {
    public static CouponIssueRequestedEvent of(String requestId, Long couponId, Long userId) {
        return new CouponIssueRequestedEvent(UUID.randomUUID().toString(), requestId, couponId, userId, ZonedDateTime.now());
    }
}