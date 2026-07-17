package com.loopers.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.config.kafka.RankingKafkaConfig;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class RankingKafkaDltIntegrationTest {

  private final KafkaTemplate<Object, Object> kafkaTemplate;
  private final KafkaProperties kafkaProperties;

  @Autowired
  RankingKafkaDltIntegrationTest(
      KafkaTemplate<Object, Object> kafkaTemplate, KafkaProperties kafkaProperties) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaProperties = kafkaProperties;
  }

  @DisplayName("처리에 계속 실패한 이벤트는 재시도 후 DLT로 이동한다.")
  @Test
  void movesInvalidEventToDltAfterRetries() throws Exception {
    String productKey = Long.toString(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
    ProductActivityEventMessage unsupportedVersionEvent =
        new ProductActivityEventMessage(
            UUID.randomUUID(),
            99,
            ProductActivityEventType.PRODUCT_VIEWED,
            Instant.parse("2026-07-16T01:00:00Z"),
            Long.parseLong(productKey),
            null,
            null);

    try (KafkaConsumer<String, byte[]> dltConsumer = createDltConsumer()) {
      dltConsumer.subscribe(List.of(RankingKafkaConfig.DLT_TOPIC));
      kafkaTemplate
          .send(RankingKafkaConfig.ACTIVITY_TOPIC, productKey, unsupportedVersionEvent)
          .get(5, TimeUnit.SECONDS);

      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      boolean found = false;
      while (System.nanoTime() < deadline && !found) {
        for (ConsumerRecord<String, byte[]> record :
            dltConsumer.poll(Duration.ofMillis(500)).records(RankingKafkaConfig.DLT_TOPIC)) {
          if (productKey.equals(record.key())) {
            found = true;
            break;
          }
        }
      }

      assertThat(found).isTrue();
    }
  }

  private KafkaConsumer<String, byte[]> createDltConsumer() {
    Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "ranking-dlt-test-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(properties, new StringDeserializer(), new ByteArrayDeserializer());
  }
}
