package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.metrics.OrderEventMessage;
import com.loopers.application.ranking.RankingProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 랭킹 전용 컨슈머 그룹(ranking) — product-metrics 그룹과 같은 토픽을 독립 오프셋으로 재소비한다.
 * 랭킹 장애/지연이 핵심 집계(product_metrics)에 전파되지 않도록 그룹을 분리했다.
 * 실패 정책은 기존 공용 에러핸들러(1s×5 재시도 → <topic>.DLT)를 그대로 쓴다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingConsumer {

    private final ObjectMapper objectMapper;
    private final RankingProcessor rankingProcessor;

    @KafkaListener(topics = "catalog-events", groupId = "ranking", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consumeCatalog(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, byte[]> record = records.get(i);
            try {
                rankingProcessor.handleCatalog(objectMapper.readValue(record.value(), CatalogEventMessage.class));
            } catch (Exception e) {
                throw new BatchListenerFailedException("catalog-events 랭킹 반영 실패 offset=" + record.offset(), e, i);
            }
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "order-events", groupId = "ranking", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consumeOrder(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, byte[]> record = records.get(i);
            try {
                rankingProcessor.handleOrder(objectMapper.readValue(record.value(), OrderEventMessage.class));
            } catch (Exception e) {
                throw new BatchListenerFailedException("order-events 랭킹 반영 실패 offset=" + record.offset(), e, i);
            }
        }
        ack.acknowledge();
    }
}
