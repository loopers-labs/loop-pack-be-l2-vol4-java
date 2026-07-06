package com.loopers.metrics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.metrics.application.ProductMetricService;
import com.loopers.support.dlq.DeadLetterPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * catalog-events 를 소비해 상품 좋아요/조회 수를 집계한다.
 * 역직렬화 실패(poison)는 DLT 로 격리하고, 처리 실패(DB 등 일시 오류)는 ack 하지 않아 재소비한다.
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
        for (ConsumerRecord<String, byte[]> record : records) {
            CatalogEventMessage message = deserialize(record);
            if (message != null) {
                productMetricService.applyCatalog(message);
            }
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
