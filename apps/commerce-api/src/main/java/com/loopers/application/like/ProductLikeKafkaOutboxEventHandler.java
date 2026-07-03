package com.loopers.application.like;

import com.loopers.domain.outbox.OutboxEventHandler;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.infrastructure.kafka.KafkaOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

// 릴레이(③)가 PRODUCT_LIKED/PRODUCT_UNLIKED outbox를 위임받아 catalog-events 토픽으로 발행한다.
@RequiredArgsConstructor
@Component
public class ProductLikeKafkaOutboxEventHandler implements OutboxEventHandler {

    private static final String TOPIC = "catalog-events";
    private static final Set<String> SUPPORTED = Set.of("PRODUCT_LIKED", "PRODUCT_UNLIKED");

    private final KafkaOutboxPublisher kafkaOutboxPublisher;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(OutboxModel outbox) {
        kafkaOutboxPublisher.publishAndMarkDone(outbox, TOPIC);
    }
}
