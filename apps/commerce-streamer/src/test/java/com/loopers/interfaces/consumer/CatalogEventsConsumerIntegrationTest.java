package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetricDailyRepository;
import com.loopers.domain.metrics.ProductMetricSummaryRepository;
import com.loopers.infrastructure.EntityId;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@EmbeddedKafka(
        partitions = 3,
        topics = {"catalog-events", "order-events", "demo.internal.topic-v1"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@Import({
        MySqlTestContainersConfig.class,
        RedisTestContainersConfig.class
})
@DisplayName("CatalogEventsConsumer 통합 테스트")
class CatalogEventsConsumerIntegrationTest {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProductMetricSummaryRepository productMetricSummaryRepository;

    @Autowired
    private ProductMetricDailyRepository productMetricDailyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("[ECP] LikeAddedEvent 수신 시 product_metrics의 like_count가 1 증가한다.")
    @Test
    void incrementsLikeCount_whenLikeAddedEventReceived() throws Exception {
        // arrange
        String productId = EntityId.generate("PRD");
        String payload = buildPayload(EntityId.generate("OBX"), "LikeAddedEvent",
                Map.of("userId", "USR_01", "productId", productId));

        // act
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, productId, payload);

        // assert
        await().atMost(10, SECONDS).untilAsserted(() -> {
            long likeCount = productMetricSummaryRepository.findByProductId(productId)
                    .map(m -> m.getLikeCount())
                    .orElse(0L);
            assertEquals(1L, likeCount);
        });
    }

    @DisplayName("[ECP] LikeRemovedEvent 수신 시 product_metrics의 like_count가 1 감소한다.")
    @Test
    void decrementsLikeCount_whenLikeRemovedEventReceived() throws Exception {
        // arrange
        String productId = EntityId.generate("PRD");
        String addPayload = buildPayload(EntityId.generate("OBX"), "LikeAddedEvent",
                Map.of("userId", "USR_01", "productId", productId));
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, productId, addPayload);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertEquals(1L, productMetricSummaryRepository.findByProductId(productId)
                        .map(m -> m.getLikeCount()).orElse(0L))
        );

        // act
        String removePayload = buildPayload(EntityId.generate("OBX"), "LikeRemovedEvent",
                Map.of("userId", "USR_01", "productId", productId));
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, productId, removePayload);

        // assert
        await().atMost(10, SECONDS).untilAsserted(() -> {
            long likeCount = productMetricSummaryRepository.findByProductId(productId)
                    .map(m -> m.getLikeCount()).orElse(0L);
            assertEquals(0L, likeCount);
        });
    }

    @DisplayName("[Idempotency] 동일한 eventId를 두 번 수신해도 like_count는 1만 증가한다.")
    @Test
    void processesEventOnlyOnce_whenSameEventIdReceivedTwice() throws Exception {
        // arrange
        String productId = EntityId.generate("PRD");
        String eventId = EntityId.generate("OBX");
        String payload = buildPayload(eventId, "LikeAddedEvent",
                Map.of("userId", "USR_01", "productId", productId));

        // act — 동일 eventId 두 번 전송
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, productId, payload);
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, productId, payload);

        // assert — 멱등 처리로 like_count는 1
        await().atMost(10, SECONDS).untilAsserted(() -> {
            long likeCount = productMetricSummaryRepository.findByProductId(productId)
                    .map(m -> m.getLikeCount()).orElse(0L);
            assertEquals(1L, likeCount);
        });
    }

    @DisplayName("[ECP] ProductViewedEvent 수신 시 product_metrics의 view_count가 1 증가한다.")
    @Test
    void incrementsViewCount_whenProductViewedEventReceived() throws Exception {
        // arrange
        String productId = EntityId.generate("PRD");
        String payload = buildPayload(EntityId.generate("OBX"), "ProductViewedEvent",
                Map.of("productId", productId, "userId", "USR_01"));

        // act
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, productId, payload);

        // assert
        await().atMost(10, SECONDS).untilAsserted(() -> {
            long viewCount = productMetricSummaryRepository.findByProductId(productId)
                    .map(m -> m.getViewCount()).orElse(0L);
            assertEquals(1L, viewCount);
        });
    }

    @DisplayName("[ECP] ProductViewedEvent 수신 시 product_metric_daily의 오늘자 view_count가 1 증가한다.")
    @Test
    void incrementsDailyViewCount_whenProductViewedEventReceived() throws Exception {
        // arrange
        String productId = EntityId.generate("PRD");
        String payload = buildPayload(EntityId.generate("OBX"), "ProductViewedEvent",
                Map.of("productId", productId, "userId", "USR_01"));

        // act
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, productId, payload);

        // assert
        await().atMost(10, SECONDS).untilAsserted(() -> {
            long viewCount = productMetricDailyRepository.findByProductIdAndMetricDate(productId, LocalDate.now())
                    .map(m -> m.getViewCount()).orElse(0L);
            assertEquals(1L, viewCount);
        });
    }

    private String buildPayload(String eventId, String eventType, Map<String, Object> data) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", ZonedDateTime.now().toString());
        envelope.put("data", data);
        return objectMapper.writeValueAsString(envelope);
    }
}
