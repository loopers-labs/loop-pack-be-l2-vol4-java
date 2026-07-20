package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.catalog.ranking.ProductRankingEventService;
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
public class ProductRankingConsumer {

    private static final String TOPIC_RANKING_EVENTS = "ranking-events";

    private final ProductRankingEventService productRankingEventService;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    @KafkaListener(
        topics = {
            "${loopers.kafka.topics.catalog-events:catalog-events}",
            "${loopers.kafka.topics.order-events:order-events}"
        },
        groupId = "${loopers.kafka.consumer-groups.product-ranking:product-ranking-consumer}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        try {
            for (ConsumerRecord<Object, Object> message : messages) {
                productRankingEventService.process(message.topic(), toEventMessage(message.value()));
            }
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            eventMetrics.recordKafkaConsumerFailure(TOPIC_RANKING_EVENTS, "UNKNOWN");
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

        eventMetrics.recordKafkaConsumerFailure(TOPIC_RANKING_EVENTS, "UNKNOWN");
        throw new IllegalArgumentException("지원하지 않는 Kafka 메시지 형식입니다.");
    }

    private EventMessage deserialize(String text) {
        try {
            return objectMapper.readValue(text, EventMessage.class);
        } catch (JsonProcessingException e) {
            eventMetrics.recordKafkaConsumerFailure(TOPIC_RANKING_EVENTS, "UNKNOWN");
            throw new IllegalArgumentException("Kafka 메시지 해석에 실패했습니다.", e);
        }
    }
}
