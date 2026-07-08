package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.outbox.OutboxEvent;
import com.loopers.application.outbox.OutboxMessagePublisher;
import com.loopers.application.outbox.OutboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiredArgsConstructor
@Component
public class KafkaOutboxMessagePublisher implements OutboxMessagePublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;

    @Override
    public void publish(OutboxEvent event) {
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload)
                .get(outboxProperties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException | InterruptedException | ExecutionException | TimeoutException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Outbox Kafka publish failed.", exception);
        }
    }
}
