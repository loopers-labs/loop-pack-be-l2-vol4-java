package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

public record CouponIssueRequestedEvent(
    String eventId,
    Long couponId,
    String userLoginId,
    String requestId,
    ZonedDateTime occurredAt
) {
    public static CouponIssueRequestedEvent of(Long couponId, String userLoginId, String requestId) {
        return new CouponIssueRequestedEvent(
            UUID.randomUUID().toString(),
            couponId,
            userLoginId,
            requestId,
            ZonedDateTime.now()
        );
    }

    public String eventType() {
        return "COUPON_ISSUE_REQUESTED";
    }

    public KafkaEventEnvelope toEnvelope() {
        return new KafkaEventEnvelope(
            eventId,
            eventType(),
            "COUPON",
            couponId,
            occurredAt,
            Map.of(
                "couponId", couponId,
                "userLoginId", userLoginId,
                "requestId", requestId
            )
        );
    }
}
