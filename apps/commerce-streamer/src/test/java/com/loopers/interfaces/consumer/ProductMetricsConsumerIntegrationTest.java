package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Kafka 브로커(Testcontainers)에 메시지를 발행해 컨슈머가 product_metrics에 반영하는지 end-to-end로 검증한다.
@SpringBootTest
class ProductMetricsConsumerIntegrationTest {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);
    private static final String ORDER_TOPIC = "order-events";
    private static final String CATALOG_TOPIC = "catalog-events";

    @Autowired
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private KafkaListenerEndpointRegistry endpointRegistry;

    // auto.offset.reset=latest(프로덕션 설정) 하에서, 토픽 자동 생성 특성상 컨슈머가 파티션을 할당받기 전에
    // 메시지를 발행하면 컨슈머가 로그 끝부터 읽어 첫 메시지(offset 0)를 건너뛴다.
    // 그래서 발행 전에 토픽을 미리 만들고 metrics-consumer 두 리스너가 파티션을 할당받을 때까지 대기한다.
    // 할당이 끝나면 컨슈머가 빈 토픽의 끝(offset 0)에 자리 잡으므로 이후 발행되는 메시지를 놓치지 않는다.
    @BeforeEach
    void waitForConsumerAssignment() {
        kafkaAdmin.createOrModifyTopics(
                TopicBuilder.name(ORDER_TOPIC).partitions(1).replicas(1).build(),
                TopicBuilder.name(CATALOG_TOPIC).partitions(1).replicas(1).build()
        );
        ContainerTestUtils.waitForAssignment(endpointRegistry.getListenerContainer("productMetricsOrderConsumer"), 1);
        ContainerTestUtils.waitForAssignment(endpointRegistry.getListenerContainer("productMetricsCatalogConsumer"), 1);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void publishOrder(String eventId, Long orderId, List<Map<String, Object>> items) throws Exception {
        String envelope = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "aggregateType", "Order",
                "aggregateId", String.valueOf(orderId),
                "eventType", "ORDER_CREATED",
                "payload", Map.of("eventId", eventId, "orderId", orderId, "userId", 1L, "items", items)
        ));
        stringKafkaTemplate.send("order-events", String.valueOf(orderId), envelope).get();
    }

    private void publishCatalog(String eventId, Long productId, String eventType) throws Exception {
        String envelope = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "aggregateType", "Product",
                "aggregateId", String.valueOf(productId),
                "eventType", eventType,
                "payload", Map.of("eventId", eventId, "productId", productId)
        ));
        stringKafkaTemplate.send("catalog-events", String.valueOf(productId), envelope).get();
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
    class ListenOrder {

        @DisplayName("주문 아이템 여러 개가 각 상품의 order_count에 정확히 반영된다.")
        @Test
        void increasesOrderCountPerItem_whenOrderCreatedPublished() throws Exception {
            // given
            Long productIdA = 200L;
            Long productIdB = 201L;
            String eventId = UUID.randomUUID().toString();
            List<Map<String, Object>> items = List.of(
                    Map.of("productId", productIdA, "quantity", 2),
                    Map.of("productId", productIdB, "quantity", 3)
            );

            // when
            publishOrder(eventId, 999L, items);

            // then
            ProductMetricsModel metricsA = awaitMetrics(productIdA);
            ProductMetricsModel metricsB = awaitMetrics(productIdB);
            assertThat(metricsA.getOrderCount()).isEqualTo(2L);
            assertThat(metricsB.getOrderCount()).isEqualTo(3L);
        }

        @DisplayName("같은 eventId(주문) 메시지를 재발행해도 order_count는 한 번만 반영된다(멱등성).")
        @Test
        void isIdempotent_whenSameOrderEventRepublished() throws Exception {
            // given
            Long productId = 202L;
            String eventId = UUID.randomUUID().toString();
            List<Map<String, Object>> items = List.of(Map.of("productId", productId, "quantity", 5));
            publishOrder(eventId, 1000L, items);
            awaitMetrics(productId);

            // when: 동일 eventId로 재전달 시뮬레이션
            publishOrder(eventId, 1000L, items);
            Thread.sleep(1000);

            // then
            ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId).orElseThrow();
            assertThat(metrics.getOrderCount()).isEqualTo(5L);
        }
    }

    @DisplayName("catalog-events 토픽에 메시지를 발행할 때,")
    @Nested
    class ListenCatalog {

        @DisplayName("PRODUCT_LIKED면 like_count가 1 증가한다.")
        @Test
        void increasesLikeCount_whenProductLikedPublished() throws Exception {
            // given
            Long productId = 100L;
            String eventId = UUID.randomUUID().toString();

            // when
            publishCatalog(eventId, productId, "PRODUCT_LIKED");

            // then
            ProductMetricsModel metrics = awaitMetrics(productId);
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("PRODUCT_VIEWED면 view_count가 1 증가한다.")
        @Test
        void increasesViewCount_whenProductViewedPublished() throws Exception {
            // given
            Long productId = 101L;
            String eventId = UUID.randomUUID().toString();

            // when
            publishCatalog(eventId, productId, "PRODUCT_VIEWED");

            // then
            ProductMetricsModel metrics = awaitMetrics(productId);
            assertThat(metrics.getViewCount()).isEqualTo(1L);
        }

        @DisplayName("같은 eventId 메시지를 재발행해도 like_count는 한 번만 반영된다(멱등성).")
        @Test
        void isIdempotent_whenSameEventRepublished() throws Exception {
            // given
            Long productId = 102L;
            String eventId = UUID.randomUUID().toString();
            publishCatalog(eventId, productId, "PRODUCT_LIKED");
            awaitMetrics(productId);

            // when: 동일 eventId로 재전달 시뮬레이션
            publishCatalog(eventId, productId, "PRODUCT_LIKED");
            Thread.sleep(1000);

            // then
            ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }
    }
}
