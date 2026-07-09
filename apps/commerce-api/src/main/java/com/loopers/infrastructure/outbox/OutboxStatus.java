package com.loopers.infrastructure.outbox;

/**
 * Outbox 행의 발행 상태. PENDING(적재됨, 미발행) → PUBLISHED(Kafka 발행 확인됨).
 *
 * <p>실패는 별도 상태로 두지 않는다 — 발행 실패한 행은 PENDING 으로 남아 relay 가 무한 재시도한다
 * (커밋된 비즈니스 사실은 반드시 전파돼야 하므로 포기하지 않는다). 브로커 장애도 복구되면 자연히 발행된다.</p>
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
