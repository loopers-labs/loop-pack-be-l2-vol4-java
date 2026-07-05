package com.loopers.application.order;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * order-events 봉투. 프로듀서(commerce-api)의 OutboxMessage 와 같은 JSON 모양을 streamer 가 자기 DTO 로 받는다.
 * data 안에 주문의 상품별 (productId, quantity) 라인이 담긴다.
 */
public record OrderEventMessage(String eventId, String eventType, Long aggregateId, JsonNode data) {
}
