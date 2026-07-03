package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;

// commerce-api가 발행하는 Kafka 이벤트 봉투에 대응하는 record.
// 두 앱이 별도 배포 단위라 공유 모듈 없이 각자 구현한다 - 이 레포에 기존 계약 공유 모듈이 없는 것과 일관된다.
public record KafkaEventEnvelope(String eventId, String aggregateType, String aggregateId, String eventType, JsonNode payload) {
}
