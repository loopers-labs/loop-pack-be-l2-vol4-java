package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.ranking.CatalogRankingEventProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CatalogRankingEventConsumer {
    private final ObjectMapper objectMapper;
    private final CatalogRankingEventProcessor catalogRankingEventProcessor;

    @KafkaListener(
        topics = "${loopers.kafka.topics.catalog-events}",
        groupId = "${loopers.kafka.consumer-groups.catalog-ranking}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, Object>> records, Acknowledgment acknowledgment) throws IOException {
        List<CatalogEventMessage> events = new ArrayList<>(records.size());
        for (ConsumerRecord<String, Object> record : records) {
            events.add(objectMapper.readValue(payload(record.value()), CatalogEventMessage.class));
        }
        catalogRankingEventProcessor.process(events);
        acknowledgment.acknowledge();
    }

    private String payload(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
