package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductSalesApplicationService;
import com.loopers.domain.eventlog.EventHandledId;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.infrastructure.eventlog.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
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
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * order-events(판매 확정) collector 를 실제 Kafka(Testcontainer)로 검증한다. 메시지를 직접 발행해
 * product_metrics.sales_count 누적과 <b>event_id 멱등</b>을 확인한다. catalog collector 와 별도 groupId·스레드지만
 * 같은 event_handled 테이블을 handler 스코프로 분리해 공유한다.
 */
@SpringBootTest
class OrderSalesCollectorIntegrationTest {

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("PRODUCT_SOLD 를 소비하면 sales_count 가 판매 수량만큼 누적된다.")
    @Test
    void consumesSold_accumulatesSalesCount() {
        long productId = 9201L;
        try (Producer<String, String> producer = newProducer()) {
            send(producer, sold(productId, 3));

            ProductMetrics metrics = awaitSalesCount(productId, 3L);
            assertThat(metrics.getSalesCount()).isEqualTo(3L);
        }
    }

    @DisplayName("같은 event_id 가 재전송돼도 sales_count 는 한 번만 누적된다(멱등).")
    @Test
    void duplicateEventId_doesNotDoubleCount() {
        long productId = 9202L;
        String duplicatedEventId = UUID.randomUUID().toString();
        String freshEventId = UUID.randomUUID().toString();

        try (Producer<String, String> producer = newProducer()) {
            // 같은 key(productId) → 같은 파티션 → 발행 순서대로 소비: e1(qty2), e1(중복), e2(qty2)
            send(producer, sold(productId, 2, duplicatedEventId));
            send(producer, sold(productId, 2, duplicatedEventId));
            send(producer, sold(productId, 2, freshEventId));

            // e2 가 반영돼 4 가 되면 그 앞의 중복 e1 은 이미 소비·skip 된 것(순서 보장).
            ProductMetrics metrics = awaitSalesCount(productId, 4L);
            assertThat(metrics.getSalesCount()).isEqualTo(4L);

            assertThat(eventHandledJpaRepository.existsById(
                    new EventHandledId(duplicatedEventId, ProductSalesApplicationService.HANDLER))).isTrue();
            assertThat(eventHandledJpaRepository.existsById(
                    new EventHandledId(freshEventId, ProductSalesApplicationService.HANDLER))).isTrue();
        }
    }

    private OrderEventMessage sold(long productId, int quantity) {
        return sold(productId, quantity, UUID.randomUUID().toString());
    }

    private OrderEventMessage sold(long productId, int quantity, String eventId) {
        return new OrderEventMessage(
                eventId, OrderEventType.PRODUCT_SOLD, productId, quantity, 700L, 1L, ZonedDateTime.now());
    }

    private void send(Producer<String, String> producer, OrderEventMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            producer.send(new ProducerRecord<>(
                    OrderSalesConsumer.ORDER_EVENTS, String.valueOf(message.productId()), payload));
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 메시지 발행 실패", e);
        }
    }

    private Producer<String, String> newProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    private ProductMetrics awaitSalesCount(long productId, long expected) {
        ProductMetrics metrics = null;
        for (int i = 0; i < 100; i++) {
            metrics = productMetricsJpaRepository.findById(productId).orElse(null);
            if (metrics != null && metrics.getSalesCount() == expected) {
                return metrics;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("product_metrics(productId=" + productId + ").sales_count 가 " + expected
                + " 로 수렴하지 못했습니다. (현재=" + (metrics == null ? "없음" : metrics.getSalesCount()) + ")");
    }
}
