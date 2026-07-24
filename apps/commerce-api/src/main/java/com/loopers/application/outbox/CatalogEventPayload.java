package com.loopers.application.outbox;

/**
 * catalog-events 토픽으로 나가는 메시지 payload(JSON). eventId는 컨슈머 멱등 처리(중복 제거)용.
 */
public record CatalogEventPayload(String eventId, String type, Long productId, Long userId) {
}
