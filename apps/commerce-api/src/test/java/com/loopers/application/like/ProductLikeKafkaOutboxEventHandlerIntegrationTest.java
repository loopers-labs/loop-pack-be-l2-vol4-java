package com.loopers.application.like;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.outbox.OutboxRelay;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// 실제 Kafka 브로커(Testcontainers)에 발행된 메시지를 컨슈머로 폴링해 검증한다.
@SpringBootTest
class ProductLikeKafkaOutboxEventHandlerIntegrationTest {

    private static final String TOPIC = "catalog-events";

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Consumer<String, String> createConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                String.join(",", kafkaProperties.getBootstrapServers()), "test-" + UUID.randomUUID(), "true");
        // KafkaTestUtils.consumerProps()는 <Integer, String> 컨슈머 기준으로 키 역직렬화기를 IntegerDeserializer로 고정한다.
        // 실제 프로듀서(stringKafkaTemplate)는 StringSerializer로 키를 쓰므로 여기서 명시적으로 덮어써야 한다.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(List.of(TOPIC));
        return consumer;
    }

    private Long saveProduct() {
        BrandModel brand = brandRepository.save(new BrandModel("브랜드"));
        return productRepository.save(new ProductModel(brand.getId(), "상품", BigDecimal.valueOf(1000))).getId();
    }

    private void savePendingOutbox(String eventId, Long productId, String eventType) {
        String payload = writePayload(eventId, productId);
        outboxRepository.save(new OutboxModel(eventId, "Product", String.valueOf(productId), eventType, payload));
    }

    private String writePayload(String eventId, Long productId) {
        try {
            return objectMapper.writeValueAsString(Map.of("eventId", eventId, "productId", productId));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // catalog-events 토픽은 같은 JVM 안에서 여러 테스트 클래스가 공유한다. 새 컨슈머는 earliest 오프셋부터
    // 읽으므로 이전 테스트가 남긴 레코드가 함께 잡힐 수 있어, eventId로 이 테스트가 발행한 레코드만 찾는다.
    private ConsumerRecord<String, String> findRecordByEventId(Consumer<String, String> consumer, String expectedEventId) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (readEventId(record.value()).equals(expectedEventId)) {
                    return record;
                }
            }
        }
        throw new AssertionError("eventId=" + expectedEventId + "에 해당하는 레코드를 찾지 못했습니다.");
    }

    private String readEventId(String value) {
        try {
            return objectMapper.readTree(value).get("eventId").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DisplayName("PRODUCT_LIKED/PRODUCT_UNLIKED 아웃박스를 릴레이가 처리할 때,")
    @Nested
    class HandlePendingOutbox {

        @DisplayName("catalog-events 토픽에 aggregateId를 키로 봉투가 발행되고 아웃박스가 DONE 처리된다.")
        @Test
        void publishesEnvelopeAndMarksDone_whenProductLikedOutboxIsPending() throws Exception {
            // given
            Long productId = saveProduct();
            String eventId = UUID.randomUUID().toString();
            savePendingOutbox(eventId, productId, "PRODUCT_LIKED");

            try (Consumer<String, String> consumer = createConsumer()) {
                // when
                outboxRelay.relay();

                // then
                ConsumerRecord<String, String> record = findRecordByEventId(consumer, eventId);
                JsonNode envelope = objectMapper.readTree(record.value());
                assertAll(
                        () -> assertThat(record.key()).isEqualTo(String.valueOf(productId)),
                        () -> assertThat(envelope.get("eventId").asText()).isEqualTo(eventId),
                        () -> assertThat(envelope.get("aggregateType").asText()).isEqualTo("Product"),
                        () -> assertThat(envelope.get("aggregateId").asText()).isEqualTo(String.valueOf(productId)),
                        () -> assertThat(envelope.get("eventType").asText()).isEqualTo("PRODUCT_LIKED"),
                        () -> assertThat(envelope.get("payload").get("productId").asLong()).isEqualTo(productId),
                        () -> assertThat(outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING)).isEmpty()
                );
            }
        }

        @DisplayName("catalog-events 토픽에 PRODUCT_UNLIKED 봉투가 발행된다.")
        @Test
        void publishesEnvelope_whenProductUnlikedOutboxIsPending() throws Exception {
            // given
            Long productId = saveProduct();
            String eventId = UUID.randomUUID().toString();
            savePendingOutbox(eventId, productId, "PRODUCT_UNLIKED");

            try (Consumer<String, String> consumer = createConsumer()) {
                // when
                outboxRelay.relay();

                // then
                ConsumerRecord<String, String> record = findRecordByEventId(consumer, eventId);
                JsonNode envelope = objectMapper.readTree(record.value());
                assertThat(envelope.get("eventType").asText()).isEqualTo("PRODUCT_UNLIKED");
            }
        }
    }
}
