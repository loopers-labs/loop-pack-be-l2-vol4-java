package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// 실제 Kafka 브로커(Testcontainers)에 이벤트를 발행해 RankingConsumer가 랭킹 ZSET에 반영하는지 end-to-end로 검증한다.
// order-events/catalog-events는 metrics-consumer(ProductMetricsConsumer)도 동일 토픽을 재구독하므로
// product_metrics에도 부수적으로 데이터가 쌓인다. 다음 테스트에 영향을 주지 않도록 함께 정리한다.
@SpringBootTest
class RankingConsumerIntegrationTest {

    // Testcontainers Kafka는 브로커가 갓 떠서 order-events/catalog-events 토픽이 아직 없는 상태로 이 클래스가
    // 가장 먼저(또는 단독으로) 실행되면, 토픽 auto-create가 전파될 때까지 몇 초간 UNKNOWN_TOPIC_OR_PARTITION
    // 재시도가 발생한다. 다른 테스트가 먼저 같은 토픽에 발행해 워밍업된 순서에 기대지 않도록 여유 있게 잡는다.
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(20);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    private String todayKey() {
        return "ranking:all:" + LocalDate.now().format(DATE_FORMAT);
    }

    private void publishOrder(Long orderId, List<Map<String, Object>> items) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String envelope = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "aggregateType", "Order",
                "aggregateId", String.valueOf(orderId),
                "eventType", "ORDER_CREATED",
                "payload", Map.of("eventId", eventId, "orderId", orderId, "userId", 1L, "items", items)
        ));
        stringKafkaTemplate.send("order-events", String.valueOf(orderId), envelope).get();
    }

    private void publishCatalog(Long productId, String eventType) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String envelope = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "aggregateType", "Product",
                "aggregateId", String.valueOf(productId),
                "eventType", eventType,
                "payload", Map.of("eventId", eventId, "productId", productId)
        ));
        stringKafkaTemplate.send("catalog-events", String.valueOf(productId), envelope).get();
    }

    private Double awaitScore(Long productId) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Double score = redisTemplate.opsForZSet().score(todayKey(), String.valueOf(productId));
            if (score != null) {
                return score;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("랭킹 ZSET 반영을 기다리는 동안 타임아웃 발생. productId=" + productId);
    }

    @DisplayName("order-events 토픽에 메시지를 발행할 때,")
    @Nested
    class ListenOrder {

        @DisplayName("주문 아이템의 가격과 수량이 가중치와 함께 오늘 랭킹 ZSET 점수에 반영된다.")
        @Test
        void addsWeightedOrderScore_whenOrderCreatedPublished() throws Exception {
            // given
            Long productId = 300L;
            List<Map<String, Object>> items = List.of(Map.of("productId", productId, "quantity", 2, "price", 10_000));

            // when
            publishOrder(9001L, items);

            // then: 기본 order 가중치 0.6 * 10000 * 2 = 12000
            assertThat(awaitScore(productId)).isCloseTo(12_000.0, within(1e-6));
        }
    }

    @DisplayName("catalog-events 토픽에 메시지를 발행할 때,")
    @Nested
    class ListenCatalog {

        @DisplayName("PRODUCT_LIKED면 좋아요 가중치만큼 오늘 랭킹 ZSET 점수가 증가한다.")
        @Test
        void addsLikeWeightScore_whenProductLikedPublished() throws Exception {
            // given
            Long productId = 301L;

            // when
            publishCatalog(productId, "PRODUCT_LIKED");

            // then: 기본 like 가중치 0.2
            assertThat(awaitScore(productId)).isCloseTo(0.2, within(1e-9));
        }

        @DisplayName("PRODUCT_VIEWED면 조회 가중치만큼 오늘 랭킹 ZSET 점수가 증가한다.")
        @Test
        void addsViewWeightScore_whenProductViewedPublished() throws Exception {
            // given
            Long productId = 302L;

            // when
            publishCatalog(productId, "PRODUCT_VIEWED");

            // then: 기본 view 가중치 0.1
            assertThat(awaitScore(productId)).isCloseTo(0.1, within(1e-9));
        }
    }
}
