package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.Topics;
import com.loopers.domain.metrics.ProductMetricModel;
import com.loopers.infrastructure.metrics.ProductMetricJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka 파이프라인 컨슈머 e2e: 토픽으로 메시지를 보내면 product_metrics가 집계되고,
 * 같은 event_id 중복 수신 시 한 번만 반영(멱등)되는지 검증한다.
 * <p>
 * 테스트에서는 처음부터 읽도록 auto.offset.reset=earliest로 덮어쓴다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.kafka.properties.auto.offset.reset=earliest")
class ProductMetricsConsumerIntegrationTest {

    @Autowired
    private ProductMetricJpaRepository productMetricJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("catalog-events의 LIKED 이벤트가 like_count로 집계된다.")
    @Test
    void aggregatesLikes() {
        Long productId = 701L;
        send(Topics.CATALOG_EVENTS, String.valueOf(productId),
            new CatalogEventMessage(UUID.randomUUID().toString(), "LIKED", productId, 1L));
        send(Topics.CATALOG_EVENTS, String.valueOf(productId),
            new CatalogEventMessage(UUID.randomUUID().toString(), "LIKED", productId, 2L));

        awaitLikeCount(productId, 2L);
    }

    @DisplayName("order-events의 상품 라인이 sales_count로 집계된다.")
    @Test
    void aggregatesSales() {
        Long p1 = 711L;
        Long p2 = 712L;
        send(Topics.ORDER_EVENTS, "1001",
            new OrderEventMessage(UUID.randomUUID().toString(), 1001L, 1L,
                List.of(new OrderEventMessage.Line(p1, 2), new OrderEventMessage.Line(p2, 1))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(salesCount(p1)).isEqualTo(2L);
            assertThat(salesCount(p2)).isEqualTo(1L);
        });
    }

    @DisplayName("멱등: 같은 event_id의 LIKED가 두 번 와도 like_count는 1만 증가한다.")
    @Test
    void isIdempotent_onDuplicateEventId() {
        Long productId = 721L;
        String duplicatedEventId = UUID.randomUUID().toString();
        CatalogEventMessage message = new CatalogEventMessage(duplicatedEventId, "LIKED", productId, 1L);

        send(Topics.CATALOG_EVENTS, String.valueOf(productId), message);
        send(Topics.CATALOG_EVENTS, String.valueOf(productId), message);

        // 중복 제거로 like_count는 1을 넘지 않는다(2로 튀지 않음).
        awaitLikeCount(productId, 1L);
    }

    // --- helpers ---

    private void awaitLikeCount(Long productId, long expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(likeCount(productId)).isEqualTo(expected));
    }

    private Long likeCount(Long productId) {
        List<ProductMetricModel> rows = productMetricJpaRepository.findByProductId(productId);
        return rows.isEmpty() ? null : rows.stream().mapToLong(ProductMetricModel::getLikeCount).sum();
    }

    private Long salesCount(Long productId) {
        List<ProductMetricModel> rows = productMetricJpaRepository.findByProductId(productId);
        return rows.isEmpty() ? null : rows.stream().mapToLong(ProductMetricModel::getSalesCount).sum();
    }

    private void send(String topic, String key, Object payload) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.kafka.bootstrap-servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            String json = objectMapper.writeValueAsString(payload);
            producer.send(new ProducerRecord<>(topic, key, json)).get();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 메시지 전송 실패", e);
        }
    }
}
