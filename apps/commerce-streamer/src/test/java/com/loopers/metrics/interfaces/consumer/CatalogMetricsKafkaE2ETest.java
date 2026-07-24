package com.loopers.metrics.interfaces.consumer;

import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.CatalogEventPayload;
import com.loopers.metrics.application.CatalogEventType;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Testcontainers
@SpringBootTest(properties = {
    "commerce.metrics.catalog.topic-name=catalog-events",
    "commerce.metrics.catalog.group-id=catalog-metrics-e2e",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.properties.auto.offset.reset=earliest"
})
class CatalogMetricsKafkaE2ETest {

    private static final String TOPIC = "catalog-events";
    private static final String DLT_TOPIC = TOPIC + ".DLT";
    private static final Long PRODUCT_ID = 101L;
    private static final Long USER_ID = 1L;
    private static final long DLT_AWAIT_SECONDS = 40;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");
    private static final LocalDate METRIC_DATE = LocalDate.of(2026, 7, 2);

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("apache/kafka-native:3.8.0")
    );

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CatalogMetricsKafkaE2ETest(
        KafkaTemplate<Object, Object> kafkaTemplate,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.properties.bootstrap.servers", KAFKA::getBootstrapServers);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("catalog-events를 실제 Kafka로 소비하면, 상품 지표와 처리 이력이 저장된다.")
    @Test
    void consumesCatalogEventsFromKafka() throws Exception {
        // arrange
        CatalogEventEnvelope liked = event("event-kafka-1", CatalogEventType.PRODUCT_LIKED, 1);
        CatalogEventEnvelope viewed = event("event-kafka-2", CatalogEventType.PRODUCT_VIEWED, 1);

        // act
        SendResult<Object, Object> first = send(liked);
        SendResult<Object, Object> second = send(viewed);

        // assert
        ProductMetricRow metric = awaitMetric(PRODUCT_ID, 1, 1);
        assertAll(
            () -> assertThat(first.getRecordMetadata().partition())
                .isEqualTo(second.getRecordMetadata().partition()),
            () -> assertThat(metric.likeDelta()).isEqualTo(1),
            () -> assertThat(metric.viewCount()).isEqualTo(1),
            () -> assertThat(handledEventCount()).isEqualTo(2)
        );
    }

    @DisplayName("처리할 수 없는 catalog 이벤트는 재시도 후 DLT로 격리한다.")
    @Test
    void sendsInvalidCatalogEventToDlt() throws Exception {
        // arrange
        byte[] invalidPayload = "{invalid-json".getBytes(StandardCharsets.UTF_8);

        // act
        sendRaw(PRODUCT_ID.toString(), invalidPayload);

        // assert
        ConsumerRecord<String, byte[]> dltRecord = awaitDltRecord(invalidPayload);
        assertAll(
            () -> assertThat(dltRecord.key()).isEqualTo(PRODUCT_ID.toString()),
            () -> assertThat(dltRecord.value()).containsExactly(invalidPayload),
            () -> assertThat(dltRecord.topic()).isEqualTo(DLT_TOPIC),
            () -> assertThat(findMetric(PRODUCT_ID)).isNull(),
            () -> assertThat(handledEventCount()).isZero()
        );
    }

    private SendResult<Object, Object> send(CatalogEventEnvelope event) throws Exception {
        SendResult<Object, Object> result = kafkaTemplate.send(TOPIC, PRODUCT_ID.toString(), event)
            .get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();
        return result;
    }

    private void sendRaw(String key, byte[] value) throws Exception {
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(rawProducerProperties())) {
            producer.send(new ProducerRecord<>(TOPIC, key, value)).get(10, TimeUnit.SECONDS);
            producer.flush();
        }
    }

    private ConsumerRecord<String, byte[]> awaitDltRecord(byte[] expectedPayload) {
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(dltConsumerProperties())) {
            consumer.subscribe(List.of(DLT_TOPIC));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DLT_AWAIT_SECONDS);
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(200))) {
                    if (Arrays.equals(record.value(), expectedPayload)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("DLT record was not published.");
    }

    private Map<String, Object> rawProducerProperties() {
        return Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer"
        );
    }

    private Map<String, Object> dltConsumerProperties() {
        return Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "catalog-dlt-reader-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer",
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer"
        );
    }

    private ProductMetricRow awaitMetric(Long productId, long likeDelta, long viewCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        ProductMetricRow metric = findMetric(productId);
        while (!matches(metric, likeDelta, viewCount) && System.nanoTime() < deadline) {
            Thread.sleep(200);
            metric = findMetric(productId);
        }
        assertThat(metric).isNotNull();
        return metric;
    }

    private boolean matches(ProductMetricRow metric, long likeDelta, long viewCount) {
        return metric != null
            && metric.likeDelta() == likeDelta
            && metric.viewCount() == viewCount;
    }

    private ProductMetricRow findMetric(Long productId) {
        try {
            return jdbcTemplate.queryForObject(
                """
                    select like_delta, view_count
                    from product_metrics
                    where metric_date = ?
                      and product_id = ?
                    """,
                (resultSet, rowNum) -> new ProductMetricRow(
                    resultSet.getLong("like_delta"),
                    resultSet.getLong("view_count")
                ),
                METRIC_DATE,
                productId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private int handledEventCount() {
        return jdbcTemplate.queryForObject("select count(*) from event_handled", Integer.class);
    }

    private CatalogEventEnvelope event(String eventId, CatalogEventType eventType, int delta) {
        return new CatalogEventEnvelope(
            eventId,
            eventType,
            "PRODUCT",
            PRODUCT_ID,
            new CatalogEventPayload(PRODUCT_ID, USER_ID, null, delta),
            OCCURRED_AT
        );
    }

    private record ProductMetricRow(long likeDelta, long viewCount) {
    }

    @TestConfiguration
    static class KafkaTopicTestConfig {

        @Bean
        NewTopic catalogEventsTopic() {
            return TopicBuilder.name(TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
        }

        @Bean
        NewTopic catalogEventsDltTopic() {
            return TopicBuilder.name(DLT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
        }
    }
}
