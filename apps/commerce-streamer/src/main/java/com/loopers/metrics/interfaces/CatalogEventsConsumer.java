package com.loopers.metrics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.metrics.application.ProductMetricService;
import com.loopers.metrics.domain.MetricStatDate;
import com.loopers.support.dlq.DeadLetterPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * catalog-events 를 소비해 상품 좋아요/조회 수를 집계한다.
 * 역직렬화 실패(poison)와 날짜를 정할 수 없는 메시지는 DLT 로 격리하고,
 * 처리 실패(DB 등 일시 오류)는 ack 하지 않아 재소비한다.
 * 날짜 판정은 여기서 한다 — DLT 발행에 원본 ConsumerRecord 가 필요하고 그건 컨슈머에만 있다.
 */
@Component
@RequiredArgsConstructor
public class CatalogEventsConsumer {

    private final ProductMetricService productMetricService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopic.CATALOG_EVENTS,
            groupId = "catalog-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        LocalDate today = MetricStatDate.today();
        for (ConsumerRecord<String, byte[]> record : records) {
            CatalogEventMessage message = deserialize(record);
            if (message == null) {
                continue;
            }
            Optional<LocalDate> statDate = MetricStatDate.of(message.occurredAt(), today);
            if (statDate.isEmpty()) {
                deadLetterPublisher.publish(record,
                        new IllegalArgumentException("occurredAt=" + message.occurredAt() + " 로는 집계 날짜를 정할 수 없다"));
                continue;
            }
            productMetricService.applyCatalog(message, statDate.get());
        }
        acknowledgment.acknowledge();
    }

    private CatalogEventMessage deserialize(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), CatalogEventMessage.class);
        } catch (IOException e) {
            deadLetterPublisher.publish(record, e);
            return null;
        }
    }
}
