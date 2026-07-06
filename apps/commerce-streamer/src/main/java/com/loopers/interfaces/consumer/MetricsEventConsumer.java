package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsAggregationService;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * commerce-api 가 발행한 이벤트를 배치로 소비해 집계에 반영한다.
 * 배치 전체 처리가 끝난 뒤에만 ack(offset 커밋) — 중간에 죽으면 배치가 재배달되고, 중복은 멱등 장부가 걸러낸다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MetricsEventConsumer {

    private final MetricsAggregationService metricsAggregationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"catalog-events", "order-events"},
        groupId = "metrics-aggregator",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "${metrics-consumer.auto-startup:true}"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                JsonNode envelope = objectMapper.readTree(record.value().toString());
                metricsAggregationService.aggregate(
                    envelope.get("eventId").asText(),
                    envelope.get("type").asText(),
                    envelope.get("payload")
                );
            } catch (Exception e) {
                // 실패 시 ack 하지 않고 예외 전파 → 배치 재배달. 처리된 앞부분은 멱등 장부가 skip 한다.
                log.error("이벤트 집계 실패 — 배치를 ack 하지 않고 재시도합니다. topic={}, offset={}",
                    record.topic(), record.offset(), e);
                throw new IllegalStateException("이벤트 집계 실패", e);
            }
        }
        acknowledgment.acknowledge();
    }
}
