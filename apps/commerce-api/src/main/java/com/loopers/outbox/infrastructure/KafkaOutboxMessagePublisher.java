package com.loopers.outbox.infrastructure;

import com.loopers.outbox.application.OutboxMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * KafkaTemplate 기반 Outbox 발행 어댑터.
 * send().get() 으로 동기 대기해 브로커 ack(acks=all)까지 확인한다.
 * 실패하면 예외를 전파해 릴레이가 해당 행을 PENDING 으로 남기고 재시도하게 한다.
 */
@Component
@RequiredArgsConstructor
public class KafkaOutboxMessagePublisher implements OutboxMessagePublisher {

    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            outboxKafkaTemplate.send(topic, key, payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("outbox 발행 중 인터럽트 topic=" + topic + " key=" + key, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("outbox 발행 실패 topic=" + topic + " key=" + key, e.getCause());
        }
    }
}
