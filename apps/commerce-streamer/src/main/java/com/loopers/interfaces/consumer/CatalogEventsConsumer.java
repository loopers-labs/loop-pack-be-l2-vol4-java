package com.loopers.interfaces.consumer;

import com.loopers.application.consumer.CatalogEventHandler;
import com.loopers.application.consumer.EventEnvelope;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * catalog-events 배치 컨슈머. 배치 내 이벤트를 하나씩 handler 로 위임.
 * <p>
 * 단일 예외로 배치 전체를 재시도하면 이미 처리된 이벤트는 event_handled 로 스킵되고,
 * 실패했던 이벤트만 재시도된다. manual Ack — 처리 완료 후에만 커밋.
 */
@RequiredArgsConstructor
@Component
public class CatalogEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventsConsumer.class);

    private final CatalogEventHandler handler;

    @KafkaListener(
        topics = "${kafka-topics.catalog-events}",
        groupId = "metrics-catalog",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onBatch(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                handler.handle(EventEnvelope.from(record));
            } catch (Exception e) {
                log.error("catalog-events 처리 실패 — Ack 유보. topic={}, offset={}, cause={}",
                    record.topic(), record.offset(), e.getMessage(), e);
                throw e;
            }
        }
        ack.acknowledge();
    }
}
