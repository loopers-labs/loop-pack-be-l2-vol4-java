package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.KafkaEventEnvelope;
import com.loopers.application.event.ProductLikeChangedEvent;
import com.loopers.application.event.ProductOrderedEvent;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class CatalogEventOutboxListener {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final CatalogEventTopicProperties topicProperties;
    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ProductLikeChangedEvent event) {
        save(event.eventId(), event.eventType().name(), event.productId(), event.toEnvelope());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ProductViewedEvent event) {
        save(event.eventId(), event.eventType().name(), event.productId(), event.toEnvelope());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ProductOrderedEvent event) {
        save(event.eventId(), event.eventType().name(), event.productId(), event.toEnvelope());
    }

    private void save(String eventId, String eventType, Long productId, KafkaEventEnvelope envelope) {
        try {
            outboxEventRepository.save(new OutboxEvent(
                eventId,
                topicProperties.catalogEvents(),
                String.valueOf(productId),
                eventType,
                objectMapper.writeValueAsString(envelope)
            ));
            meterRegistry.counter("outbox_event_saved_total", "eventType", eventType).increment();
        } catch (JsonProcessingException exception) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "카탈로그 이벤트 직렬화에 실패했습니다.");
        }
    }
}
