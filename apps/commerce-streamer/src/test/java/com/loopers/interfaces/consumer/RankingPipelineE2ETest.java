package com.loopers.interfaces.consumer;

import com.loopers.config.redis.RedisConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.productmetrics.ProductMetricsModel;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.infrastructure.productmetrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 랭킹 파이프라인 E2E — 실제 Kafka 브로커(Testcontainers)에 이벤트를 발행하고,
 * 실제 컨슈머가 컨슘해 product_metrics(DB)와 랭킹 ZSET(Redis)에 반영되는 전체 흐름을 검증한다.
 * 발행 payload JSON은 commerce-api producer의 계약과 동일한 형태로 작성한다.
 */
@SpringBootTest
class RankingPipelineE2ETest {

    @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired private KafkaListenerEndpointRegistry listenerRegistry;
    @Autowired private ProductMetricsJpaRepository productMetricsJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final double OFFSET = 1e-6;

    private String todayKey() {
        return RankingKey.daily(LocalDate.now());
    }

    @BeforeEach
    void waitForListenerAssignment() {
        // auto-offset-reset=latest — 컨슈머가 파티션을 할당받기 전에 발행하면 메시지를 놓치므로 할당 완료까지 대기
        Awaitility.await().atMost(TIMEOUT).until(() ->
            listenerRegistry.getListenerContainers().stream().allMatch(container -> {
                var assigned = container.getAssignedPartitions();
                return assigned != null && !assigned.isEmpty();
            })
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void publishCatalogEvent(String eventId, String eventType, Long productId) {
        String payload = """
            {"eventId":"%s","eventType":"%s","userId":1,"productId":%d}""".formatted(eventId, eventType, productId);
        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, String.valueOf(productId), payload);
    }

    private void publishOrderPaidEvent(String eventId, Long productId, int quantity, int price) {
        String payload = """
            {"eventId":"%s","eventType":"ORDER_PAID","orderId":100,"userId":1,\
            "items":[{"productId":%d,"quantity":%d,"price":%d}]}""".formatted(eventId, productId, quantity, price);
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, "100", payload);
    }

    private Double score(Long productId) {
        return redisTemplate.opsForZSet().score(todayKey(), String.valueOf(productId));
    }

    @DisplayName("이벤트 발행 → 컨슘 → 반영 E2E 흐름에서,")
    @Nested
    class Pipeline {

        @DisplayName("조회·좋아요·주문 이벤트가 컨슘되어 DB 집계와 랭킹 ZSET 점수(0.1 + 0.2 + 0.6×단가×수량)에 함께 반영된다.")
        @Test
        void reflectsEventsToMetricsAndRanking() {
            // arrange
            Long productId = 10L;

            // act
            publishCatalogEvent(UUID.randomUUID().toString(), "PRODUCT_VIEWED", productId);
            publishCatalogEvent(UUID.randomUUID().toString(), "PRODUCT_LIKED", productId);
            publishOrderPaidEvent(UUID.randomUUID().toString(), productId, 2, 5_000);

            // assert — 0.1(조회) + 0.2(좋아요) + 0.6×5000×2(주문) = 6000.3
            Awaitility.await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(score(productId)).isNotNull().isCloseTo(6_000.3, within(OFFSET))
            );
            ProductMetricsModel metrics = productMetricsJpaRepository.findByProductId(productId).orElseThrow();
            assertAll(
                () -> assertThat(metrics.getViewCount()).isEqualTo(1),
                () -> assertThat(metrics.getLikeCount()).isEqualTo(1),
                () -> assertThat(metrics.getSalesCount()).isEqualTo(2)
            );
        }

        @DisplayName("주문 1건이 좋아요 3건보다 랭킹 상위에 온다 (가중치 검증).")
        @Test
        void ordersOutrankLikes_byWeight() {
            // arrange
            Long orderedProduct = 1L;   // 주문 1건 → 0.6 × 1000 × 1 = 600
            Long likedProduct = 2L;     // 좋아요 3건 → 0.6

            // act
            publishOrderPaidEvent(UUID.randomUUID().toString(), orderedProduct, 1, 1_000);
            for (int i = 0; i < 3; i++) {
                publishCatalogEvent(UUID.randomUUID().toString(), "PRODUCT_LIKED", likedProduct);
            }

            // assert — 두 상품 점수가 모두 반영된 뒤 순서 비교
            Awaitility.await().atMost(TIMEOUT).untilAsserted(() -> assertAll(
                () -> assertThat(score(orderedProduct)).isNotNull().isCloseTo(600.0, within(OFFSET)),
                () -> assertThat(score(likedProduct)).isNotNull().isCloseTo(0.6, within(OFFSET))
            ));
            Set<String> ranking = redisTemplate.opsForZSet().reverseRange(todayKey(), 0, -1);
            assertThat(ranking).containsExactly("1", "2");
        }

        @DisplayName("같은 eventId가 중복 발행되어도 점수는 한 번만 반영된다 (멱등).")
        @Test
        void reflectsScoreOnlyOnce_whenEventIsDuplicated() {
            // arrange
            Long productId = 30L;
            String duplicatedEventId = UUID.randomUUID().toString();

            // act — 같은 이벤트 2회 발행 후, 처리 완료 지점을 알기 위한 후행 이벤트 1건 발행
            publishCatalogEvent(duplicatedEventId, "PRODUCT_LIKED", productId);
            publishCatalogEvent(duplicatedEventId, "PRODUCT_LIKED", productId);
            publishCatalogEvent(UUID.randomUUID().toString(), "PRODUCT_VIEWED", productId);

            // assert — 같은 key(productId)라 같은 파티션에서 순서 보장: 후행 조회(0.1)까지 반영된 시점에
            // 좋아요가 1회만 반영됐다면 0.2 + 0.1 = 0.3 (중복 반영 시 0.5)
            Awaitility.await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(score(productId)).isNotNull().isCloseTo(0.3, within(OFFSET))
            );
            ProductMetricsModel metrics = productMetricsJpaRepository.findByProductId(productId).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1);
        }
    }
}
