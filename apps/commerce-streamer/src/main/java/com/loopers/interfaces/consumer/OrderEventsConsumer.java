package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventsConsumer {

    private static final String SUPPORTED_EVENT_TYPE = "ORDER_CREATED";

    private final MetricsFacade metricsFacade;
    private final ObjectMapper objectMapper;

    @KafkaListener(id = "orderEventsConsumer", topics = "order-events", groupId = "metrics-consumer")
    public void listen(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        try {
            KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
            if (SUPPORTED_EVENT_TYPE.equals(envelope.eventType())) {
                List<MetricsFacade.SalesItem> items = objectMapper.convertValue(
                        envelope.payload().get("items"), new TypeReference<List<OrderItemPayload>>() {
                        }
                ).stream()
                        .map(item -> new MetricsFacade.SalesItem(item.productId(), item.quantity()))
                        .toList();
                metricsFacade.applySales(envelope.eventId(), items);
            } else {
                log.warn("처리할 수 없는 이벤트 타입입니다. eventType={}", envelope.eventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("order-events 처리 실패. key={}", record.key(), e);
        }
    }

    private record OrderItemPayload(Long productId, Long quantity) {
    }
}
