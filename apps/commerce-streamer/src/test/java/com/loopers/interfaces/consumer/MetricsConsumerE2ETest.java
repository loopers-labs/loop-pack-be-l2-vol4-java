package com.loopers.interfaces.consumer;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class MetricsConsumerE2ETest {

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainersConfig::getBootstrapServers);
    }

    @Autowired private ProductMetricsRepository metricsRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("catalog-events(LikeAdded)를 발행하면 product_metrics.like_count 가 반영된다.")
    @Test
    void likeMetricApplied() {
        String payload = "{\"eventId\":\"e1\",\"type\":\"LikeAdded\",\"productId\":100,"
            + "\"likeCount\":7,\"version\":2,\"occurredAt\":\"2026-07-02T00:00:00Z\"}";

        // consumer group(product-metrics)이 auto.offset.reset=latest 로 첫 rebalance 를 마치기 전에
        // 발행하면 그 메시지는 건너뛰어질 수 있어(신규 그룹 join race), 소비될 때까지 재발행한다.
        // MetricsProcessor 의 event_handled 멱등 가드 덕분에 동일 eventId 재발행은 안전하다.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            publish("catalog-events", "100", payload);
            assertThat(metricsRepository.find(100L)).isPresent()
                .get().extracting(m -> m.getLikeCount()).isEqualTo(7L);
        });
    }

    private void publish(String topic, String key, String value) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainersConfig.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(p)) {
            producer.send(new ProducerRecord<>(topic, key, value));
            producer.flush();
        }
    }
}
