package com.loopers.application.order;

import com.loopers.domain.outbox.OutboxEventHandler;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.infrastructure.kafka.KafkaOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 릴레이(③)가 ORDER_CREATED outbox를 위임받아 order-events 토픽으로 발행한다.
@RequiredArgsConstructor
@Component
public class OrderCreatedKafkaOutboxEventHandler implements OutboxEventHandler {

    private static final String TOPIC = "order-events";
    private static final String SUPPORTED_EVENT_TYPE = "ORDER_CREATED";

    private final KafkaOutboxPublisher kafkaOutboxPublisher;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED_EVENT_TYPE.equals(eventType);
    }

    @Override
    public void handle(OutboxModel outbox) {
        kafkaOutboxPublisher.publishAndMarkDone(outbox, TOPIC);
    }
}
