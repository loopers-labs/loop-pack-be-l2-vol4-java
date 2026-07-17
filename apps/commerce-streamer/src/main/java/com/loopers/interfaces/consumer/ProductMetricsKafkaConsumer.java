package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsEventProcessor;
import com.loopers.config.kafka.RankingKafkaConfig;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductMetricsKafkaConsumer {

  public static final String METRICS_GROUP_ID = "commerce-product-metrics-v1";

  private final ObjectMapper objectMapper;
  private final ProductMetricsEventProcessor metricsEventProcessor;

  @KafkaListener(
      topics = RankingKafkaConfig.ACTIVITY_TOPIC,
      groupId = METRICS_GROUP_ID,
      containerFactory = RankingKafkaConfig.RECORD_LISTENER_FACTORY)
  public void consume(ConsumerRecord<String, byte[]> record) throws IOException {
    ProductActivityEventMessage event =
        objectMapper.readValue(record.value(), ProductActivityEventMessage.class);
    metricsEventProcessor.process(event);
  }
}
