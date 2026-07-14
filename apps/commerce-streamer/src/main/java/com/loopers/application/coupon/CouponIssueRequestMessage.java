package com.loopers.application.coupon;

import java.time.ZonedDateTime;
import java.util.Map;

public record CouponIssueRequestMessage(
    String eventId,
    String eventType,
    String aggregateType,
    Long aggregateId,
    ZonedDateTime occurredAt,
    Map<String, Object> data
) {
    public Long couponId() {
        return aggregateId;
    }

    public String userLoginId() {
        return stringValue("userLoginId");
    }

    public String requestId() {
        return stringValue("requestId");
    }

    private String stringValue(String key) {
        Object value = data == null ? null : data.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
