package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * poison 메시지(처리 시 반드시 실패)가 재시도 소진 후 {@code catalog-events.DLT} 로 격리되고, 그 뒤에 온 정상 메시지는
 * 계속 처리되는지(파티션이 막히지 않는지)를 실제 Kafka(Testcontainer)로 검증한다.
 *
 * <p>poison 과 정상 메시지를 <b>같은 key</b>로 보내 같은 파티션에 태운다 — DLQ 가 없다면 poison 이 뒤 메시지를 영구히
 * 막지만, DLQ 가 있으면 poison 격리 후 정상 메시지가 반영되어야 한다.</p>
 */
@SpringBootTest
class CatalogEventDlqIntegrationTest {

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

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

    @DisplayName("처리 실패 메시지는 재시도 소진 후 catalog-events.DLT 로 격리되고, 같은 파티션의 정상 메시지는 계속 처리된다.")
    @Test
    void poisonMessage_isRoutedToDlt_andPipelineContinues() {
        long productId = 9101L;
        String partitionKey = String.valueOf(productId);
        String poisonEventId = UUID.randomUUID().toString();

        try (Producer<String, String> producer = newProducer();
             KafkaConsumer<String, String> dltConsumer = newDltConsumer()) {
            dltConsumer.subscribe(List.of(CatalogEventConsumer.CATALOG_EVENTS + ".DLT"));

            // poison: productId=null → apply() 안에서 findById(null) 로 반드시 실패한다.
            send(producer, partitionKey, poison(poisonEventId));
            // 같은 key(같은 파티션)로 뒤이어 온 정상 메시지 — DLQ 로 poison 이 비켜야 이게 처리된다.
            send(producer, partitionKey, liked(productId));

            // 1) 정상 메시지가 반영됐다 = poison 이 파티션을 막지 않고 DLT 로 비켜났다.
            ProductMetrics metrics = awaitLikeCount(productId, 1L);
            assertThat(metrics.getLikeCount()).isEqualTo(1L);

            // 2) poison 은 DLT 에 원문 그대로 격리됐다.
            ConsumerRecord<String, String> dltRecord = awaitDltRecordWithEventId(dltConsumer, poisonEventId);
            assertThat(dltRecord.value()).contains(poisonEventId);

            // 3) 운영자가 "왜 실패했는지 + 원본이 어디서 왔는지" 볼 수 있도록 실패 메타가 헤더로 실린다.
            //    (payload 는 원문 무변형 → 재처리 시 원 토픽에 그대로 되돌릴 수 있다.)
            Headers headers = dltRecord.headers();
            assertThat(headerAsString(headers, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                    .isEqualTo(CatalogEventConsumer.CATALOG_EVENTS);
            assertThat(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION)).isNotNull();
            assertThat(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
            assertThat(headerAsString(headers, KafkaHeaders.DLT_EXCEPTION_FQCN)).contains("Exception");
            assertThat(headerAsString(headers, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNotBlank();
            // 결정적 poison(필수값 null)은 IllegalArgumentException 으로 즉시 격리된다 → 스택에 근본 원인이 남는다.
            assertThat(headerAsString(headers, KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).contains("IllegalArgumentException");
        }
    }

    private String headerAsString(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private CatalogEventMessage poison(String eventId) {
        // productId=null 이 poison 트리거(집계 시 findById(null) 실패).
        return new CatalogEventMessage(eventId, CatalogEventType.PRODUCT_LIKED, null, 1L, ZonedDateTime.now());
    }

    private CatalogEventMessage liked(long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), CatalogEventType.PRODUCT_LIKED, productId, 1L, ZonedDateTime.now());
    }

    private void send(Producer<String, String> producer, String key, CatalogEventMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            producer.send(new ProducerRecord<>(CatalogEventConsumer.CATALOG_EVENTS, key, payload));
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

    private KafkaConsumer<String, String> newDltConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlt-verify-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecord<String, String> awaitDltRecordWithEventId(KafkaConsumer<String, String> consumer, String eventId) {
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(eventId)) {
                    return record;
                }
            }
        }
        throw new AssertionError("catalog-events.DLT 에서 eventId=" + eventId + " 격리 메시지를 수신하지 못했습니다.");
    }

    private ProductMetrics awaitLikeCount(long productId, long expected) {
        ProductMetrics metrics = null;
        for (int i = 0; i < 150; i++) {
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
}
