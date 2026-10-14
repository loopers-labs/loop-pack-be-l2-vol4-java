package com.loopers.infrastructure.eventpublish;

import com.loopers.application.eventpublish.OutboxRelayPort;
import com.loopers.domain.eventpublish.OutboxMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka 를 통해 Outbox 메시지를 발행하는 어댑터.
 * <p>
 * partition key 로 순서 보장. eventId 를 header 로 붙여 Consumer 의 멱등 판단에 활용한다.
 * <p>
 * <b>send().get(timeout)</b> 로 명시적 ack 확인 — acks=all + idempotence=true 조합으로 브로커 저장이 보장된 뒤에만
 * Relay 가 SENT 로 마킹하도록 한다.
 */
@Component
public class OutboxKafkaPublisher implements OutboxRelayPort {

    static final String HEADER_EVENT_ID = "event-id";
    static final String HEADER_EVENT_TYPE = "event-type";
    static final String HEADER_AGGREGATE_TYPE = "aggregate-type";
    static final String HEADER_AGGREGATE_ID = "aggregate-id";

    private static final long SEND_TIMEOUT_SECONDS = 10L;

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public OutboxKafkaPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OutboxMessage message) {
        ProducerRecord<Object, Object> record = new ProducerRecord<>(
            message.getTopic(),
            null,
            message.getPartitionKey(),
            message.getPayload().getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(new RecordHeader(HEADER_EVENT_ID, bytes(message.getEventId())));
        record.headers().add(new RecordHeader(HEADER_EVENT_TYPE, bytes(message.getEventType())));
        record.headers().add(new RecordHeader(HEADER_AGGREGATE_TYPE, bytes(message.getAggregateType())));
        record.headers().add(new RecordHeader(HEADER_AGGREGATE_ID, bytes(message.getAggregateId())));
        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 발행 중 인터럽트", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka 발행 실패: " + e.getMessage(), e);
        }
    }

    private static byte[] bytes(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}
