package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 collector(전용 consumer group, 배치 리스너)를 실제 Kafka + Redis(Testcontainers)로 못박는 통합 테스트
 * — 배치 팩토리(BatchMessagingMessageConverter, List 파라미터 변환)가 실제 발행 → raw 보드 반영까지 연결되는지 포함.
 *
 * <p>캐시된 기본 컨텍스트를 그대로 쓴다(프로퍼티 오버라이드 없음) — 기본 컨텍스트가 프로덕션 group 의
 * 단독 소유자라 리밸런스 경합이 없다. earliest 라 스위트 이전 메시지를 재생하지만 상품 ID 가 클래스
 * 고유라 무해하고, DLQ 테스트의 poison(productId=null, JSON 유효)은 배치 경로의 요소 단위 skip 으로 흡수된다.</p>
 *
 * <p>같은 토픽을 metrics collector 도 소비하므로(그룹 분리 구조의 본질) 메시지가 product_metrics 에도 반영된다
 * — 테스트 격리를 위해 상품 ID 를 테스트마다 다르게 쓰고 DB·Redis 를 모두 정리한다.</p>
 *
 * <p>멱등 검증이 metrics 쪽과 정반대임에 주의: 랭킹은 <b>의도적으로 dedup 이 없어</b> 같은 event_id 재전송이
 * 두 번 가산돼야 한다(결정 B — 근사 예산 수용, 대가로 offset 리셋 재구축 확보).</p>
 */
@SpringBootTest
class RankingEventConsumerIntegrationTest {

    private static final LocalDate TODAY = RankingKeys.bucketOf(ZonedDateTime.now());

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("PRODUCT_LIKED 2건 + PRODUCT_UNLIKED 1건 → like raw 보드 점수 1 (취소 감점 반영)")
    @Test
    void likeSignal_reflectsUnlikeAsDecrement() {
        long productId = 8101L;
        try (Producer<String, String> producer = newProducer()) {
            sendCatalog(producer, catalog(CatalogEventType.PRODUCT_LIKED, productId));
            sendCatalog(producer, catalog(CatalogEventType.PRODUCT_LIKED, productId));
            sendCatalog(producer, catalog(CatalogEventType.PRODUCT_UNLIKED, productId));

            awaitScore(RankingKeys.raw(RankingSignal.LIKE, TODAY), productId, 1.0);
        }
    }

    @DisplayName("같은 상품 PRODUCT_VIEWED 3건이 배치 경로로 view 보드 3.0 에 수렴한다")
    @Test
    void viewSignal_accumulates() {
        long productId = 8102L;
        try (Producer<String, String> producer = newProducer()) {
            sendCatalog(producer, catalog(CatalogEventType.PRODUCT_VIEWED, productId));
            sendCatalog(producer, catalog(CatalogEventType.PRODUCT_VIEWED, productId));
            sendCatalog(producer, catalog(CatalogEventType.PRODUCT_VIEWED, productId));

            awaitScore(RankingKeys.raw(RankingSignal.VIEW, TODAY), productId, 3.0);
        }
    }

    @DisplayName("PRODUCT_SOLD(수량 3) → order_count 보드 1, order_qty 보드 3 — 건수와 수량이 분리 보존된다")
    @Test
    void orderSignal_splitsCountAndQuantity() {
        long productId = 8103L;
        try (Producer<String, String> producer = newProducer()) {
            sendOrder(producer, new OrderEventMessage(
                    UUID.randomUUID().toString(), OrderEventType.PRODUCT_SOLD, productId, 3, 700L, 1L,
                    ZonedDateTime.now()));

            awaitScore(RankingKeys.raw(RankingSignal.ORDER_COUNT, TODAY), productId, 1.0);
            awaitScore(RankingKeys.raw(RankingSignal.ORDER_QTY, TODAY), productId, 3.0);
        }
    }

    @DisplayName("같은 event_id 재전송은 두 번 가산된다 — 랭킹 collector 는 의도적으로 멱등 장치가 없다(결정 B)")
    @Test
    void duplicateEventId_isCountedTwice() {
        long productId = 8104L;
        String duplicatedEventId = UUID.randomUUID().toString();
        try (Producer<String, String> producer = newProducer()) {
            sendCatalog(producer, new CatalogEventMessage(
                    duplicatedEventId, CatalogEventType.PRODUCT_VIEWED, productId, null, ZonedDateTime.now()));
            sendCatalog(producer, new CatalogEventMessage(
                    duplicatedEventId, CatalogEventType.PRODUCT_VIEWED, productId, null, ZonedDateTime.now()));

            awaitScore(RankingKeys.raw(RankingSignal.VIEW, TODAY), productId, 2.0);
        }
    }

    @DisplayName("occurredAt 이 전일이면 전일 raw 보드로 귀속된다 — 처리 시각이 아닌 발생 시각 기준")
    @Test
    void lateEvent_goesToItsOccurredAtBucket() {
        long productId = 8105L;
        ZonedDateTime yesterday = ZonedDateTime.now(RankingKeys.ZONE).minusDays(1);
        try (Producer<String, String> producer = newProducer()) {
            sendCatalog(producer, new CatalogEventMessage(
                    UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED, productId, null, yesterday));

            awaitScore(RankingKeys.raw(RankingSignal.VIEW, TODAY.minusDays(1)), productId, 1.0);
        }
    }

    private CatalogEventMessage catalog(CatalogEventType type, long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), type, productId, 1L, ZonedDateTime.now());
    }

    private void sendCatalog(Producer<String, String> producer, CatalogEventMessage message) {
        send(producer, CatalogEventConsumer.CATALOG_EVENTS, String.valueOf(message.productId()), message);
    }

    private void sendOrder(Producer<String, String> producer, OrderEventMessage message) {
        send(producer, OrderSalesConsumer.ORDER_EVENTS, String.valueOf(message.productId()), message);
    }

    private void send(Producer<String, String> producer, String topic, String key, Object message) {
        try {
            producer.send(new ProducerRecord<>(topic, key, objectMapper.writeValueAsString(message)));
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

    /** 대기 상한 30초 — 그룹 최초 조인 + earliest 전체 재생 시간을 덮는다. */
    private void awaitScore(String key, long productId, double expected) {
        Double score = null;
        for (int i = 0; i < 100; i++) {
            score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            if (score != null && Math.abs(score - expected) < 1e-9) {
                assertThat(score).isEqualTo(expected);
                return;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError(key + "(productId=" + productId + ") 점수가 " + expected
                + " 로 수렴하지 못했습니다. (현재=" + score + ")");
    }
}
