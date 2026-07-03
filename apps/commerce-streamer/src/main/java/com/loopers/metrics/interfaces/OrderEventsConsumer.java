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
 * order-events 를 소비해 상품 판매량을 집계한다.
 * - manual ack: 배치 전체 처리 후 한 번 커밋. 처리 중 실패하면 ack 하지 않아 재소비된다(멱등이 중복 흡수).
 * - 역직렬화 실패(poison)는 DLT 로 격리하고 다음 레코드로 넘어간다.
 */
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final ProductMetricService productMetricService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopic.ORDER_EVENTS,
            groupId = "metrics-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<String, byte[]> record : records) {
            OrderPaidMessage message = deserialize(record);
            if (message != null) {
                productMetricService.apply(message);
            }
        }
        acknowledgment.acknowledge();
    }

    private OrderPaidMessage deserialize(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), OrderPaidMessage.class);
        } catch (IOException e) {
            deadLetterPublisher.publish(record, e);
            return null;
        }
    }
}
