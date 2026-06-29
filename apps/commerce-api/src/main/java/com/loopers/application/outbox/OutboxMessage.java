package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Kafka 로 발행되는 메시지 봉투(envelope).
 * outbox 행의 라우팅·식별 메타데이터(eventId/eventType/aggregateId)와 도메인 본문(data)을 함께 실어,
 * Consumer 가 메시지 하나만으로 멱등(eventId)·집계(data)를 처리할 수 있게 한다(self-contained).
 */
public record OutboxMessage(String eventId, String eventType, Long aggregateId, JsonNode data) {
}
