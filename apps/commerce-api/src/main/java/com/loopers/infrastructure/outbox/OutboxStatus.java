package com.loopers.infrastructure.outbox;

/**
 * Outbox 메시지의 발행 상태.
 *
 * <ul>
 *   <li>{@link #PENDING} — 도메인 트랜잭션과 함께 기록된 발행 대기 메시지. 릴레이가 폴링 대상으로 삼는다.</li>
 *   <li>{@link #SENT} — Kafka 전송 성공(ack) 후 마킹. 더 이상 폴링하지 않는다.</li>
 *   <li>{@link #FAILED} — 재시도 한도 초과 등 영구 실패. 운영 알림/수동 개입 대상.</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
