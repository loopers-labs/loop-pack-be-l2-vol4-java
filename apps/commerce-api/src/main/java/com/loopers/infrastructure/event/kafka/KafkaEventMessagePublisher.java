package com.loopers.infrastructure.event.kafka;

import com.loopers.application.event.relay.EventMessagePublisher;
import com.loopers.application.event.relay.EventPublishResult;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.kafka.event.EventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class KafkaEventMessagePublisher implements EventMessagePublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public EventPublishResult publish(EventOutbox outbox) {
        EventMessage message = new EventMessage(
            outbox.getEventId(),
            outbox.getEventType(),
            outbox.getAggregateType(),
            outbox.getAggregateId(),
            outbox.getPayload(),
            outbox.getCreatedAt()
        );

        try {
            kafkaTemplate.send(outbox.getTopic(), outbox.getPartitionKey(), message)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return EventPublishResult.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EventPublishResult.failed("Kafka 발행이 인터럽트되었습니다.");
        } catch (Exception e) {
            return EventPublishResult.failed(e.getMessage());
        }
    }
}
