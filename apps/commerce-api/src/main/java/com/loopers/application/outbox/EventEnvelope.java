package com.loopers.application.outbox;

/**
 * Kafka 로 실어 나르는 이벤트 봉투. value 에 eventType 을 담아, 한 토픽에 여러 이벤트가 섞여도
 * 컨슈머가 타입을 구분할 수 있게 한다. aggregateId 는 파티션 키다.
 */
public record EventEnvelope(String eventId, String eventType, Long aggregateId, Object payload) {
}