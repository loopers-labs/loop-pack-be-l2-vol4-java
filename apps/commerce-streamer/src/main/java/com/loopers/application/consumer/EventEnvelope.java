package com.loopers.application.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/**
 * Kafka 레코드에서 이벤트 메타(id/type/aggregate) + payload 를 뽑아내는 얇은 어댑터.
 * Consumer 코드가 헤더 문자열 조작으로부터 자유롭게 한다.
 */
public record EventEnvelope(
    String eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    String payload
) {

    public static final String HEADER_EVENT_ID = "event-id";
    public static final String HEADER_EVENT_TYPE = "event-type";
    public static final String HEADER_AGGREGATE_TYPE = "aggregate-type";
    public static final String HEADER_AGGREGATE_ID = "aggregate-id";

    public static EventEnvelope from(ConsumerRecord<?, ?> record) {
        String eventId = header(record, HEADER_EVENT_ID);
        String eventType = header(record, HEADER_EVENT_TYPE);
        String aggregateType = header(record, HEADER_AGGREGATE_TYPE);
        String aggregateId = header(record, HEADER_AGGREGATE_ID);
        String payload = toStringValue(record.value());
        return new EventEnvelope(eventId, eventType, aggregateType, aggregateId, payload);
    }

    public boolean hasEventId() {
        return eventId != null && !eventId.isBlank();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static String toStringValue(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return value.toString();
    }
}
