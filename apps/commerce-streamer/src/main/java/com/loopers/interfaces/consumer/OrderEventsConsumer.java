package com.loopers.interfaces.consumer;

import com.loopers.application.consumer.EventEnvelope;
import com.loopers.application.consumer.OrderEventHandler;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsConsumer.class);

    private final OrderEventHandler handler;

    @KafkaListener(
        topics = "${kafka-topics.order-events}",
        groupId = "metrics-order",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onBatch(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                handler.handle(EventEnvelope.from(record));
            } catch (Exception e) {
                log.error("order-events 처리 실패 — Ack 유보. topic={}, offset={}, cause={}",
                    record.topic(), record.offset(), e.getMessage(), e);
                throw e;
            }
        }
        ack.acknowledge();
    }
}
