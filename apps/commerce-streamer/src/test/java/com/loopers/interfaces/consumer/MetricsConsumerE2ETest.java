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

    // 페이로드의 occurredAt(2026-07-02T00:00:00Z) 을 KST 로 환산한 일자 — 집계 로우의 metric_date
    private static final java.time.LocalDate KST_DATE = java.time.LocalDate.of(2026, 7, 2);

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
            assertThat(metricsRepository.find(100L, KST_DATE)).isPresent()
                .get().extracting(m -> m.getLikeCount()).isEqualTo(7L);
        });
    }

    @DisplayName("catalog-events(ProductViewed)를 발행하면 product_metrics.view_count 가 반영되고, 같은 eventId 재전달은 멱등이다.")
    @Test
    void viewMetricAppliedIdempotently() {
        String payload = "{\"eventId\":\"v1\",\"type\":\"ProductViewed\",\"productId\":200,"
            + "\"likeCount\":0,\"version\":0,\"occurredAt\":\"2026-07-02T00:00:00Z\"}";

        // 같은 eventId 를 소비될 때까지 재발행 — event_handled 멱등 덕분에 view_count 는 1에 고정되어야 한다.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            publish("catalog-events", "200", payload);
            assertThat(metricsRepository.find(200L, KST_DATE)).isPresent()
                .get().extracting(m -> m.getViewCount()).isEqualTo(1L);
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
