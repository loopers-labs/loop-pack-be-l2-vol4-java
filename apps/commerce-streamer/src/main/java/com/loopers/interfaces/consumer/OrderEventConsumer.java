package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsProcessor;
import com.loopers.application.metrics.OrderEventMessage;
import com.loopers.confg.kafka.KafkaConfig;
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
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final MetricsProcessor metricsProcessor;

    @KafkaListener(topics = "order-events", groupId = "product-metrics", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, byte[]> record : records) {
            OrderEventMessage msg;
            try {
                msg = objectMapper.readValue(record.value(), OrderEventMessage.class);
            } catch (Exception e) {
                log.error("order-events 파싱 실패(skip) offset={}", record.offset(), e); // poison: skip
                continue;
            }
            metricsProcessor.handleOrder(msg); // 처리 실패는 전파 → 배치 미-ack → 재전달(멱등 재처리)
        }
        ack.acknowledge();
    }
}
