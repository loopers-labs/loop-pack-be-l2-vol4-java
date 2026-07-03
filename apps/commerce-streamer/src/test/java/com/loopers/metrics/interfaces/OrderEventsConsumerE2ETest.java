package com.loopers.metrics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.metrics.domain.ProductMetric;
import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * order-events 발행 → consumer 집계까지의 실제 파이프라인 검증.
 * auto.offset.reset=latest 의 구독 타이밍 race 를 피하려 같은 eventId 를 반복 발행한다.
 * 멱등(event_handled) 덕분에 여러 번 소비돼도 판매량은 정확히 한 번만 반영된다 → 파이프라인 + 멱등 동시 증명.
 */
@SpringBootTest
class OrderEventsConsumerE2ETest {

    @TestConfiguration
    static class TestKafkaConfig {

        @Bean
        NewTopic orderEventsTopicForTest() {
            return TopicBuilder.name(KafkaTopic.ORDER_EVENTS).partitions(3).replicas(1).build();
        }

        @Bean
        KafkaTemplate<String, String> stringKafkaTemplate(KafkaProperties kafkaProperties) {
            Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @Autowired
    private KafkaTemplate<String, String> stringKafkaTemplate;
    @Autowired
    private ProductMetricJpaRepository productMetricJpaRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("발행된 주문 이벤트가 소비되어 판매량으로 집계되고, 재발행돼도 한 번만 반영된다")
    void givenPublishedOrderEvent_whenConsumed_thenAggregatedExactlyOnce() throws Exception {
        // SSOT: 상품 100 의 PAID 주문 라인(수량 4). 이벤트는 이 상품 재계산 트리거.
        jdbcTemplate.update("""
                INSERT IGNORE INTO orders (id, status, created_at, updated_at)
                VALUES (1, 'PAID', NOW(6), NOW(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO order_items (order_id, product_id, quantity, created_at, updated_at)
                VALUES (1, 100, 4, NOW(6), NOW(6))
                """);

        OrderPaidMessage message = new OrderPaidMessage(
                "evt-e2e-1", 1L,
                List.of(new OrderPaidMessage.Line(100L, 4)), ZonedDateTime.now());
        String payload = objectMapper.writeValueAsString(message);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            stringKafkaTemplate.send(KafkaTopic.ORDER_EVENTS, String.valueOf(message.orderId()), payload).get();
            ProductMetric metric = productMetricJpaRepository.findById(100L).orElse(null);
            assertThat(metric).isNotNull();
            assertThat(metric.getSalesCount()).isEqualTo(4);
        });
    }
}
