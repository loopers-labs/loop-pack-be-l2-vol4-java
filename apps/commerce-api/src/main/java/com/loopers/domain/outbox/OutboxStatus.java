package com.loopers.domain.outbox;

public enum OutboxStatus {
    PENDING,    // 기록됨, 아직 Kafka 로 발행 전
    PUBLISHED,  // Kafka 발행 완료
}
