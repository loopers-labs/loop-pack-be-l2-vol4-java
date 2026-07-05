package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.metrics.MetricsProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private final ObjectMapper objectMapper;
    private final MetricsProcessor metricsProcessor;

    @KafkaListener(topics = "catalog-events", groupId = "product-metrics", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, byte[]> record = records.get(i);
            try {
                CatalogEventMessage msg = objectMapper.readValue(record.value(), CatalogEventMessage.class);
                metricsProcessor.handleCatalog(msg); // 처리 실패는 전파 → 재시도(멱등 재처리) → 소진 시 DLT
            } catch (Exception e) {
                // 실패 레코드 인덱스를 넘겨 그 레코드만 재시도/DLT — 앞의 성공분은 커밋, 뒤는 이어서 처리(파티션 비블로킹)
                throw new BatchListenerFailedException("catalog-events 처리 실패 offset=" + record.offset(), e, i);
            }
        }
        ack.acknowledge();
    }
}
