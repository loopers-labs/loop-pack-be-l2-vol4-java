package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsApplicationService;
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
 * collector 파이프를 실제 Kafka(Testcontainer)로 못박는 통합 테스트. catalog-events 로 메시지를 직접 발행해
 * (producer 인 commerce-api 없이 소비자 계약만 검증) product_metrics 반영과 <b>event_id 멱등</b>을 확인한다.
 *
 * <p>테스트 간 격리는 상품 ID 를 서로 다르게 써서 확보한다(streamer 엔 Product 테이블이 없어 ID 재사용 충돌이 없다).
 * 같은 key(productId)로 보낸 메시지는 파티션 직렬화로 발행 순서대로 소비되므로, 뒤 메시지가 반영되면 앞 메시지도
 * 반드시 소비된 것 — 이 순서 보장을 멱등 검증에 활용한다.</p>
 */
@SpringBootTest
class CatalogEventCollectorIntegrationTest {

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

    @DisplayName("PRODUCT_LIKED 를 소비하면 product_metrics.like_count 가 1 증가한다.")
    @Test
    void consumesLiked_incrementsLikeCount() {
        long productId = 9001L;
        try (Producer<String, String> producer = newProducer()) {
            send(producer, liked(productId));

            ProductMetrics metrics = awaitLikeCount(productId, 1L);
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("같은 event_id 가 재전송돼도 like_count 는 한 번만 증가한다(멱등).")
    @Test
    void duplicateEventId_doesNotDoubleCount() {
        long productId = 9002L;
        String duplicatedEventId = UUID.randomUUID().toString();
        String freshEventId = UUID.randomUUID().toString();

        try (Producer<String, String> producer = newProducer()) {
            // 같은 key(productId) → 같은 파티션 → 발행 순서대로 소비: e1, e1(중복), e2
            send(producer, liked(productId, duplicatedEventId));
            send(producer, liked(productId, duplicatedEventId));
            send(producer, liked(productId, freshEventId));

            // e2 가 반영돼 2 가 되면 그 앞의 중복 e1 은 이미 소비·skip 된 것(순서 보장).
            ProductMetrics metrics = awaitLikeCount(productId, 2L);
            assertThat(metrics.getLikeCount()).isEqualTo(2L);

            assertThat(eventHandledJpaRepository.existsById(
                    new EventHandledId(duplicatedEventId, ProductMetricsApplicationService.HANDLER))).isTrue();
            assertThat(eventHandledJpaRepository.existsById(
                    new EventHandledId(freshEventId, ProductMetricsApplicationService.HANDLER))).isTrue();
        }
    }

    @DisplayName("PRODUCT_UNLIKED 를 소비하면 like_count 가 감소한다.")
    @Test
    void consumesUnliked_decrementsLikeCount() {
        long productId = 9003L;
        try (Producer<String, String> producer = newProducer()) {
            send(producer, liked(productId));
            send(producer, liked(productId));
            send(producer, unliked(productId));

            ProductMetrics metrics = awaitLikeCount(productId, 1L);
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("PRODUCT_VIEWED 를 소비하면 product_metrics.view_count 가 증가한다(직접 발행 경로).")
    @Test
    void consumesViewed_incrementsViewCount() {
        long productId = 9004L;
        try (Producer<String, String> producer = newProducer()) {
            send(producer, viewed(productId));
            send(producer, viewed(productId));

            ProductMetrics metrics = awaitViewCount(productId, 2L);
            assertThat(metrics.getViewCount()).isEqualTo(2L);
        }
    }

    private CatalogEventMessage liked(long productId) {
        return liked(productId, UUID.randomUUID().toString());
    }

    private CatalogEventMessage liked(long productId, String eventId) {
        return new CatalogEventMessage(eventId, CatalogEventType.PRODUCT_LIKED, productId, 1L, ZonedDateTime.now());
    }

    private CatalogEventMessage unliked(long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), CatalogEventType.PRODUCT_UNLIKED, productId, 1L, ZonedDateTime.now());
    }

    private CatalogEventMessage viewed(long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED, productId, null, ZonedDateTime.now());
    }

    private void send(Producer<String, String> producer, CatalogEventMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            producer.send(new ProducerRecord<>(
                    CatalogEventConsumer.CATALOG_EVENTS, String.valueOf(message.productId()), payload));
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

    private ProductMetrics awaitLikeCount(long productId, long expected) {
        ProductMetrics metrics = null;
        for (int i = 0; i < 100; i++) {
            metrics = productMetricsJpaRepository.findById(productId).orElse(null);
            if (metrics != null && metrics.getLikeCount() == expected) {
                return metrics;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("product_metrics(productId=" + productId + ").like_count 가 " + expected
                + " 로 수렴하지 못했습니다. (현재=" + (metrics == null ? "없음" : metrics.getLikeCount()) + ")");
    }

    private ProductMetrics awaitViewCount(long productId, long expected) {
        ProductMetrics metrics = null;
        for (int i = 0; i < 100; i++) {
            metrics = productMetricsJpaRepository.findById(productId).orElse(null);
            if (metrics != null && metrics.getViewCount() == expected) {
                return metrics;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("product_metrics(productId=" + productId + ").view_count 가 " + expected
                + " 로 수렴하지 못했습니다. (현재=" + (metrics == null ? "없음" : metrics.getViewCount()) + ")");
    }
}
