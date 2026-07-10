package com.loopers.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;

// Kafka로 나가는 이벤트의 공통 봉투. outbox 경유/best-effort 직접 발행 양쪽 모두 이 형태로 통일해
// 컨슈머가 발행 경로와 무관하게 하나의 파싱 로직만 갖도록 한다.
public record KafkaEventEnvelope(String eventId, String aggregateType, String aggregateId, String eventType, JsonNode payload) {
}
