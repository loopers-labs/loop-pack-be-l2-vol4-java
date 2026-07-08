package com.loopers.application.metrics;

import java.time.ZonedDateTime;
import java.util.Map;

public record CatalogEventMessage(
    String eventId,
    String eventType,
    String aggregateType,
    Long aggregateId,
    ZonedDateTime occurredAt,
    Map<String, Object> data
) {
    public Long productId() {
        return aggregateId;
    }

    public int likeCountDelta() {
        return intValue("likeCountDelta");
    }

    public int viewCountDelta() {
        return intValue("viewCountDelta");
    }

    public int salesCountDelta() {
        return intValue("salesCountDelta");
    }

    private int intValue(String key) {
        Object value = data == null ? null : data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            return Integer.parseInt(stringValue);
        }
        return 0;
    }
}
