package com.loopers.tddstudy.application.metrics;

import com.loopers.tddstudy.messaging.CatalogEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CatalogEventConsumer {

    private final MetricsService metricsService;

    public CatalogEventConsumer(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @KafkaListener(topics = CatalogEvent.TOPIC, groupId = "collector")
    public void consume(CatalogEvent event, Acknowledgment ack) {
        metricsService.apply(event);
        ack.acknowledge();     // manual ack: 처리 성공 후 커밋
    }
}
