package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetrics;
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
 * <b>서로 다른 collector 의 동시 최초 생성 경쟁</b>을 못 박는 테스트.
 *
 * <p>같은 신규 productId 에 대해 첫 VIEWED(catalog-events → product-metrics collector)와 첫 SOLD(order-events →
 * product-sales collector)가 동시에 도착하면, 두 collector 는 서로 다른 스레드라 둘 다 {@code findById().orElseGet(of)}
 * 로 빈 행을 보고 INSERT 를 시도할 수 있다. {@code product_id} 가 PK 라 한쪽만 성공하고 다른 쪽은 무결성 위반으로
 * 실패→Kafka 재시도→이번엔 기존 행을 찾아 UPDATE 로 반영된다(자가 치유).</p>
 *
 * <p>경쟁이 실제로 발생하든 아니든 <b>최종 상태는 반드시 두 카운터 모두 보존</b>({@code view=1, sales=1})이어야 하므로,
 * 이 단언은 결정적이다(플래키 아님). 파티션 직렬화가 "한 카운터=단일 writer"만 보장하고 "행 최초 생성"은 두 collector
 * 사이에 경쟁이 있음을, 그럼에도 손실 없이 수렴함을 증명한다.</p>
 */
@SpringBootTest
class ProductMetricsConcurrentCreateIntegrationTest {

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

    @DisplayName("같은 신규 상품에 첫 조회와 첫 판매가 동시에 와도 view/sales 카운터가 모두 보존된다(최초 생성 경쟁 자가 치유).")
    @Test
    void concurrentFirstCreate_fromTwoCollectors_preservesAllCounters() {
        long productId = 9301L;
        String key = String.valueOf(productId);

        try (Producer<String, String> producer = newProducer()) {
            CatalogEventMessage viewed = new CatalogEventMessage(
                    UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED, productId, null, ZonedDateTime.now());
            OrderEventMessage sold = new OrderEventMessage(
                    UUID.randomUUID().toString(), OrderEventType.PRODUCT_SOLD, productId, 1, 700L, 1L, ZonedDateTime.now());

            // 두 토픽에 거의 동시에 발행 → 서로 다른 collector 스레드가 같은 신규 행을 동시에 만들려 경쟁한다.
            send(producer, CatalogEventConsumer.CATALOG_EVENTS, key, viewed);
            send(producer, OrderSalesConsumer.ORDER_EVENTS, key, sold);
            producer.flush();

            ProductMetrics metrics = awaitBothCounters(productId, 1L, 1L);
            assertThat(metrics.getViewCount()).isEqualTo(1L);
            assertThat(metrics.getSalesCount()).isEqualTo(1L);
        }
    }

    private void send(Producer<String, String> producer, String topic, String key, Object message) {
        try {
            producer.send(new ProducerRecord<>(topic, key, objectMapper.writeValueAsString(message)));
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

    private ProductMetrics awaitBothCounters(long productId, long expectedView, long expectedSales) {
        ProductMetrics metrics = null;
        for (int i = 0; i < 150; i++) {
            metrics = productMetricsJpaRepository.findById(productId).orElse(null);
            if (metrics != null && metrics.getViewCount() == expectedView && metrics.getSalesCount() == expectedSales) {
                return metrics;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("product_metrics(productId=" + productId + ") 가 view=" + expectedView
                + ", sales=" + expectedSales + " 로 수렴하지 못했습니다. (현재="
                + (metrics == null ? "없음" : "view=" + metrics.getViewCount() + ", sales=" + metrics.getSalesCount()) + ")");
    }
}
