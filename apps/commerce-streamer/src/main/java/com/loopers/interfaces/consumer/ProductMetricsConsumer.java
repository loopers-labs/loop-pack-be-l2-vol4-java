package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.KafkaConsumerConfig;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * catalog-events / order-events 를 소비해 상품 집계를 반영한다.
 * envelope 의 eventType 으로 분기하고, 집계 반영(멱등 포함)이 끝난 뒤에만 manual ack 로 오프셋을 커밋한다.
 * 처리 중 예외가 나면 ack 하지 않아 재전달되며, 멱등 처리가 중복 반영을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMetricsConsumer {

    private final ProductMetricsService productMetricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"catalog-events", "order-events"},
            groupId = "product-metrics",
            containerFactory = KafkaConsumerConfig.METRICS_LISTENER
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record.value());
        acknowledgment.acknowledge();
    }

    void handle(String message) {
        EventEnvelope envelope = parse(message);
        switch (envelope.eventType()) {
            case ProductMetricsService.TYPE_LIKE_CHANGED -> {
                JsonNode payload = envelope.payload();
                long delta = "LIKED".equals(payload.get("action").asText()) ? 1L : -1L;
                productMetricsService.applyLike(envelope.eventId(), payload.get("productId").asLong(), delta);
            }
            case ProductMetricsService.TYPE_PRODUCT_VIEWED ->
                    productMetricsService.applyView(envelope.eventId(), envelope.payload().get("productId").asLong());
            case ProductMetricsService.TYPE_ORDER_PAID -> {
                List<ProductMetricsService.OrderItem> items = new ArrayList<>();
                for (JsonNode item : envelope.payload().get("items")) {
                    items.add(new ProductMetricsService.OrderItem(
                            item.get("productId").asLong(),
                            item.get("quantity").asLong()
                    ));
                }
                productMetricsService.applyOrderPaid(envelope.eventId(), items);
            }
            default -> log.warn("알 수 없는 이벤트 타입 - eventType={}, eventId={}", envelope.eventType(), envelope.eventId());
        }
    }

    private EventEnvelope parse(String message) {
        try {
            return objectMapper.readValue(message, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 역직렬화 실패: " + message, e);
        }
    }
}