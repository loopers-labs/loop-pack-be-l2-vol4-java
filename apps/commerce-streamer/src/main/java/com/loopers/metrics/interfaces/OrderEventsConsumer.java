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
 * order-events 를 소비해 상품 판매량을 집계한다.
 * - manual ack: 배치 전체 처리 후 한 번 커밋. 처리 중 실패하면 ack 하지 않아 재소비된다(멱등이 중복 흡수).
 * - 역직렬화 실패(poison)와 날짜를 정할 수 없는 메시지는 DLT 로 격리하고 다음 레코드로 넘어간다.
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
        LocalDate today = MetricStatDate.today();
        for (ConsumerRecord<String, byte[]> record : records) {
            OrderPaidMessage message = deserialize(record);
            if (message == null) {
                continue;
            }
            Optional<LocalDate> statDate = MetricStatDate.of(message.paidAt(), today);
            if (statDate.isEmpty()) {
                deadLetterPublisher.publish(record,
                        new IllegalArgumentException("paidAt=" + message.paidAt() + " 로는 집계 날짜를 정할 수 없다"));
                continue;
            }
            productMetricService.apply(message, statDate.get());
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
