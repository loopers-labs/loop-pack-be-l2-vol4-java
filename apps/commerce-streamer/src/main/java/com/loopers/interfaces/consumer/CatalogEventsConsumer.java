package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.catalog.CatalogEventFacade;
import com.loopers.application.catalog.CatalogEventMessage;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventsConsumer {

    private final ObjectMapper objectMapper;
    private final CatalogEventFacade catalogEventFacade;

    @KafkaListener(topics = "catalog-events", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        List<CatalogEventMessage> messages = records.stream()
            .map(this::parse)
            .toList();
        catalogEventFacade.handle(messages);
        acknowledgment.acknowledge();   // 처리 끝난 뒤에만 커밋 — 실패 시 미커밋 → 배치 재전송 → 멱등이 흡수
    }

    private CatalogEventMessage parse(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), CatalogEventMessage.class);
        } catch (IOException e) {
            throw new IllegalStateException("catalog-events 역직렬화 실패 (offset=" + record.offset() + ")", e);
        }
    }
}
