package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.metrics.CatalogMetricsEventProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private final ObjectMapper objectMapper;
    private final CatalogMetricsEventProcessor catalogMetricsEventProcessor;

    @KafkaListener(
        topics = {"${loopers.kafka.topics.catalog-events}"},
        groupId = "${loopers.kafka.consumer-groups.catalog-metrics}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, Object>> records, Acknowledgment acknowledgment) throws IOException {
        for (ConsumerRecord<String, Object> record : records) {
            CatalogEventMessage event = objectMapper.readValue(payload(record.value()), CatalogEventMessage.class);
            catalogMetricsEventProcessor.process(event);
        }
        acknowledgment.acknowledge();
    }

    private String payload(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
