package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order-events",
        groupId = "commerce-streamer",
        containerFactory = KafkaConfig.STRING_LISTENER
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String eventId = extractOutboxId(record.value());
        try {
            process(eventId, record.value());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[OrderEventsConsumer] 처리 실패: eventId={}", eventId, e);
        }
    }

    @Transactional
    public void process(String eventId, String payload) throws Exception {
        if (eventHandledRepository.existsById(eventId)) {
            return;
        }

        JsonNode root = objectMapper.readTree(payload);
        JsonNode items = root.get("data").get("items");

        for (JsonNode item : items) {
            Long productId = item.get("productId").asLong();
            long quantity = item.get("quantity").asLong();

            ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetricsModel.create(productId));

            metrics.addSalesCount(quantity);
            productMetricsRepository.save(metrics);
        }

        eventHandledRepository.save(EventHandledModel.of(eventId));
    }

    private String extractOutboxId(String payload) {
        try {
            return objectMapper.readTree(payload).get("outboxId").asText();
        } catch (Exception e) {
            throw new RuntimeException("outboxId 추출 실패", e);
        }
    }
}
