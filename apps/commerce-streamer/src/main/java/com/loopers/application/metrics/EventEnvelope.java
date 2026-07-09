package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 소비측(commerce-streamer)이 해석하는 공통 이벤트 envelope. 발행측(commerce-api)의
 * {@code infrastructure.outbox.EventEnvelope} 와 <b>필드 계약이 동일</b>해야 역직렬화된다
 * (kafka.yml {@code spring.json.add.type.headers=false} → 컨슈머 메서드 시그니처 타입으로 매핑).
 *
 * <p>토픽 하나가 여러 {@code eventType}을 실어 나르므로(catalog-events = LIKE_CHANGED + PRODUCT_VIEWED),
 * {@code eventType}으로 분기하고 {@code payload}(타입별 본문)를 {@link JsonNode}로 받아 해석한다.
 * {@code eventId}는 발행측 outbox PK(전역 고유) = 소비자 멱등 키다.
 */
public record EventEnvelope(
        Long eventId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        long version,
        String occurredAt,
        JsonNode payload
) {
}
