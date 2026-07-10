package com.loopers.application.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.outbox.OutboxRelay;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// 실제 Kafka 브로커(Testcontainers)에 발행된 메시지를 컨슈머로 폴링해 검증한다.
@SpringBootTest
class OrderCreatedKafkaOutboxEventHandlerIntegrationTest {

    private static final String TOPIC = "order-events";

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxRepository outboxRepository;

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

    private void savePendingOutbox(String eventId, Long orderId) {
        String payload = writePayload(eventId, orderId);
        outboxRepository.save(new OutboxModel(eventId, "Order", String.valueOf(orderId), "ORDER_CREATED", payload));
    }

    private String writePayload(String eventId, Long orderId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "orderId", orderId,
                    "userId", 1L,
                    "items", List.of(Map.of("productId", 10L, "quantity", 2L))
            ));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // order-events 토픽은 같은 JVM 안에서 여러 테스트 클래스(OutboxRelay를 mock하지 않고 실제 주문을 생성하는
    // 테스트 포함)가 공유한다. 새 컨슈머는 earliest 오프셋부터 읽으므로 다른 테스트가 남긴 레코드가 함께 잡힐
    // 수 있어, eventId로 이 테스트가 발행한 레코드만 찾는다.
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

    @DisplayName("ORDER_CREATED 아웃박스를 릴레이가 처리할 때,")
    @Nested
    class HandlePendingOutbox {

        @DisplayName("order-events 토픽에 orderId를 키로 봉투가 발행되고 아웃박스가 DONE 처리된다.")
        @Test
        void publishesEnvelopeAndMarksDone_whenOrderCreatedOutboxIsPending() throws Exception {
            // given
            Long orderId = 123L;
            String eventId = UUID.randomUUID().toString();
            savePendingOutbox(eventId, orderId);

            try (Consumer<String, String> consumer = createConsumer()) {
                // when
                outboxRelay.relay();

                // then
                ConsumerRecord<String, String> record = findRecordByEventId(consumer, eventId);
                JsonNode envelope = objectMapper.readTree(record.value());
                assertAll(
                        () -> assertThat(record.key()).isEqualTo(String.valueOf(orderId)),
                        () -> assertThat(envelope.get("eventId").asText()).isEqualTo(eventId),
                        () -> assertThat(envelope.get("aggregateType").asText()).isEqualTo("Order"),
                        () -> assertThat(envelope.get("aggregateId").asText()).isEqualTo(String.valueOf(orderId)),
                        () -> assertThat(envelope.get("eventType").asText()).isEqualTo("ORDER_CREATED"),
                        () -> assertThat(envelope.get("payload").get("items").get(0).get("productId").asLong()).isEqualTo(10L),
                        () -> assertThat(outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING)).isEmpty()
                );
            }
        }
    }
}
