package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.catalog.metrics.ProductMetricsEventService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.kafka.event.EventMessage;
import com.loopers.support.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductMetricsConsumer {

    private static final String TOPIC_CATALOG_EVENTS = "catalog-events";

    private final ProductMetricsEventService productMetricsEventService;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    @KafkaListener(
        topics = {"${loopers.kafka.topics.catalog-events:catalog-events}"},
        groupId = "${loopers.kafka.consumer-groups.product-metrics:product-metrics-consumer}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        try {
            for (ConsumerRecord<Object, Object> message : messages) {
                productMetricsEventService.process(toEventMessage(message.value()));
            }
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            eventMetrics.recordKafkaConsumerFailure(TOPIC_CATALOG_EVENTS, "UNKNOWN");
            throw e;
        }
    }

    private EventMessage toEventMessage(Object value) {
        if (value instanceof EventMessage eventMessage) {
            return eventMessage;
        }
        if (value instanceof byte[] bytes) {
            return deserialize(new String(bytes, StandardCharsets.UTF_8));
        }
        if (value instanceof String text) {
            return deserialize(text);
        }

        eventMetrics.recordKafkaConsumerFailure(TOPIC_CATALOG_EVENTS, "UNKNOWN");
        throw new IllegalArgumentException("지원하지 않는 Kafka 메시지 형식입니다.");
    }

    private EventMessage deserialize(String text) {
        try {
            return objectMapper.readValue(text, EventMessage.class);
        } catch (JsonProcessingException e) {
            eventMetrics.recordKafkaConsumerFailure(TOPIC_CATALOG_EVENTS, "UNKNOWN");
            throw new IllegalArgumentException("Kafka 메시지 해석에 실패했습니다.", e);
        }
    }
}
