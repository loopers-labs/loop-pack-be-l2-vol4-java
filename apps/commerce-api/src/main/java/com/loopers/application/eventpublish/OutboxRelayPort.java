package com.loopers.application.eventpublish;

import com.loopers.domain.eventpublish.OutboxMessage;

/**
 * Outbox 릴레이가 실제 발행 경로에 의존하지 않도록 하는 포트.
 * 구현체: Kafka 를 통해 발행하는 OutboxKafkaPublisher.
 */
public interface OutboxRelayPort {

    /**
     * outbox 메시지를 발행. 성공하면 정상 반환, 실패하면 예외.
     */
    void publish(OutboxMessage message);
}
