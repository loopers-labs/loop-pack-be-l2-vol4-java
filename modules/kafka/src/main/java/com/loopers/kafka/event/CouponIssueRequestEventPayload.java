package com.loopers.kafka.event;

import java.time.ZonedDateTime;

public record CouponIssueRequestEventPayload(
    Long requestId,
    Long couponTemplateId,
    String userId,
    ZonedDateTime requestedAt
) {
}
