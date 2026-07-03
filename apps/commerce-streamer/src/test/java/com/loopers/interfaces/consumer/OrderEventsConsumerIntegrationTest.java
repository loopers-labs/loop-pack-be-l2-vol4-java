package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Kafka 브로커(Testcontainers)에 메시지를 발행해 컨슈머가 product_metrics에 반영하는지 end-to-end로 검증한다.
@SpringBootTest
class OrderEventsConsumerIntegrationTest {

    private static final String TOPIC = "order-events";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void publish(String eventId, Long orderId, List<Map<String, Object>> items) throws Exception {
        String envelope = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "aggregateType", "Order",
                "aggregateId", String.valueOf(orderId),
                "eventType", "ORDER_CREATED",
                "payload", Map.of("eventId", eventId, "orderId", orderId, "userId", 1L, "items", items)
        ));
        stringKafkaTemplate.send(TOPIC, String.valueOf(orderId), envelope).get();
    }

    private ProductMetricsModel awaitMetrics(Long productId) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<ProductMetricsModel> found = productMetricsRepository.findByProductId(productId);
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("product_metrics 반영을 기다리는 동안 타임아웃 발생. productId=" + productId);
    }

    @DisplayName("order-events 토픽에 메시지를 발행할 때,")
    @Nested
    class Listen {

        @DisplayName("주문 아이템 여러 개가 각 상품의 sales_count에 정확히 반영된다.")
        @Test
        void increasesSalesCountPerItem_whenOrderCreatedPublished() throws Exception {
            // given
            Long productIdA = 200L;
            Long productIdB = 201L;
            String eventId = UUID.randomUUID().toString();
            List<Map<String, Object>> items = List.of(
                    Map.of("productId", productIdA, "quantity", 2),
                    Map.of("productId", productIdB, "quantity", 3)
            );

            // when
            publish(eventId, 999L, items);

            // then
            ProductMetricsModel metricsA = awaitMetrics(productIdA);
            ProductMetricsModel metricsB = awaitMetrics(productIdB);
            assertThat(metricsA.getSalesCount()).isEqualTo(2L);
            assertThat(metricsB.getSalesCount()).isEqualTo(3L);
        }

        @DisplayName("같은 eventId(주문) 메시지를 재발행해도 sales_count는 한 번만 반영된다(멱등성).")
        @Test
        void isIdempotent_whenSameOrderEventRepublished() throws Exception {
            // given
            Long productId = 202L;
            String eventId = UUID.randomUUID().toString();
            List<Map<String, Object>> items = List.of(Map.of("productId", productId, "quantity", 5));
            publish(eventId, 1000L, items);
            awaitMetrics(productId);

            // when: 동일 eventId로 재전달 시뮬레이션
            publish(eventId, 1000L, items);
            Thread.sleep(1000);

            // then
            ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId).orElseThrow();
            assertThat(metrics.getSalesCount()).isEqualTo(5L);
        }
    }
}
