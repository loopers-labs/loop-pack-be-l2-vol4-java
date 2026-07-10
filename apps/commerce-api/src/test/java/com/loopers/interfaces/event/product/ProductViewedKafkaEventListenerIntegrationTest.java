package com.loopers.interfaces.event.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.AopTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// send()는 @Async라 주입된 프록시로 호출하면 별도 스레드로 넘어간다.
// 프록시를 벗긴 대상 객체로 호출해 본문(발행 배선)을 동기·결정적으로 검증한다(LikeFastPathIntegrationTest 패턴 재사용).
@SpringBootTest
class ProductViewedKafkaEventListenerIntegrationTest {

    private static final String TOPIC = "catalog-events";

    @Autowired
    private ProductViewedKafkaEventListener listener;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private ProductViewedKafkaEventListener target;

    @BeforeEach
    void setUp() {
        target = AopTestUtils.getUltimateTargetObject(listener);
    }

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

    @DisplayName("상품 조회 이벤트를 처리할 때,")
    @Nested
    class Send {

        @DisplayName("catalog-events 토픽에 productId를 키로 PRODUCT_VIEWED 봉투가 발행된다.")
        @Test
        void publishesEnvelope_whenProductViewedEventSent() throws Exception {
            // given
            Long productId = 42L;
            ProductViewedEvent event = ProductViewedEvent.of(productId);

            try (Consumer<String, String> consumer = createConsumer()) {
                // when
                target.send(event);

                // then
                ConsumerRecord<String, String> record = findRecordByEventId(consumer, event.eventId());
                JsonNode envelope = objectMapper.readTree(record.value());
                assertAll(
                        () -> assertThat(record.key()).isEqualTo(String.valueOf(productId)),
                        () -> assertThat(envelope.get("eventId").asText()).isEqualTo(event.eventId()),
                        () -> assertThat(envelope.get("aggregateType").asText()).isEqualTo("Product"),
                        () -> assertThat(envelope.get("aggregateId").asText()).isEqualTo(String.valueOf(productId)),
                        () -> assertThat(envelope.get("eventType").asText()).isEqualTo("PRODUCT_VIEWED")
                );
            }
        }
    }
}
