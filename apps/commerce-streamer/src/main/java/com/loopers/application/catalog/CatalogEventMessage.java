package com.loopers.application.catalog;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * catalog-events 봉투. 프로듀서(commerce-api)의 OutboxMessage 와 같은 JSON 모양을 streamer 가 자기 DTO 로 받는다.
 * (spring.json.add.type.headers=false 라 타입 헤더 없이 구조적으로 역직렬화 — 서비스 간 클래스 결합 없음)
 */
public record CatalogEventMessage(String eventId, String eventType, Long aggregateId, JsonNode data) {
}
