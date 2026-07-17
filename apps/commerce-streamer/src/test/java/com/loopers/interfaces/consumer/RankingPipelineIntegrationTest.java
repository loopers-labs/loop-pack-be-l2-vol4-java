package com.loopers.interfaces.consumer;

import com.loopers.interfaces.consumer.message.CatalogEventMessage;
import com.loopers.interfaces.consumer.message.LikeEventMessage;
import com.loopers.interfaces.consumer.message.OrderEventMessage;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * catalog-events/order-events/like-events 토픽에 실제로 메시지를 발행해서,
 * Consumer가 소비한 뒤 랭킹 ZSET에 점수가 반영되는지까지 검증한다.
 *
 * 새로 생성된 토픽은 컨슈머 그룹의 파티션 할당(리밸런싱)이 끝나기 전에는 메시지를 보내도
 * auto.offset.reset=latest 설정 때문에 그 메시지를 놓칠 수 있다.
 * @BeforeAll 에서 각 리스너 컨테이너의 파티션 할당이 끝날 때까지 직접 기다린 뒤 본 테스트를 시작한다.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
class RankingPipelineIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long AWAIT_TIMEOUT_MS = 15_000;
    private static final long PARTITION_ASSIGNMENT_TIMEOUT_MS = 60_000;
    private static final long AWAIT_INTERVAL_MS = 300;
    private static final List<String> TARGET_TOPICS = List.of("catalog-events", "order-events", "like-events");

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    @Qualifier("masterRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry endpointRegistry;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeAll
    void waitForConsumerGroupAssignment() {
        for (MessageListenerContainer container : endpointRegistry.getListenerContainers()) {
            String[] topics = container.getContainerProperties().getTopics();
            if (topics != null && Arrays.stream(topics).anyMatch(TARGET_TOPICS::contains)) {
                waitForPartitions(container);
            }
        }
    }

    private void waitForPartitions(MessageListenerContainer container) {
        long deadline = System.currentTimeMillis() + PARTITION_ASSIGNMENT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            var assigned = container.getAssignedPartitions();
            if (assigned != null && !assigned.isEmpty()) {
                return;
            }
            sleep();
        }
        throw new AssertionError("파티션 할당 대기 타임아웃(" + PARTITION_ASSIGNMENT_TIMEOUT_MS + "ms): " + container.getListenerId());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String rankingKey(ZonedDateTime occurredAt) {
        return "ranking:all:" + occurredAt.format(KEY_DATE_FORMAT);
    }

    private Double awaitScore(String key, Long productId) {
        return awaitUntil(
            () -> redisTemplate.opsForZSet().score(key, String.valueOf(productId)),
            score -> score != null
        );
    }

    private <T> T awaitUntil(java.util.function.Supplier<T> supplier, Predicate<T> condition) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            T value = supplier.get();
            if (condition.test(value)) {
                return value;
            }
            sleep();
        }
        throw new AssertionError("타임아웃(" + AWAIT_TIMEOUT_MS + "ms) 내에 조건을 만족하지 못했습니다.");
    }

    private void sleep() {
        try {
            Thread.sleep(AWAIT_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @DisplayName("PRODUCT_VIEWED 이벤트를 발행하면, 랭킹 ZSET에 조회 점수가 반영된다.")
    @Test
    void catalogEvent_reflectsViewScore_inRankingZSet() {
        // arrange
        Long productId = 1001L;
        ZonedDateTime occurredAt = ZonedDateTime.now(ZONE);
        CatalogEventMessage message = new CatalogEventMessage(
            UUID.randomUUID().toString(), "PRODUCT_VIEWED", productId, occurredAt
        );

        // act
        kafkaTemplate.send("catalog-events", String.valueOf(productId), message);

        // assert
        Double score = awaitScore(rankingKey(occurredAt), productId);
        assertThat(score).isEqualTo(0.1);
    }

    @DisplayName("ORDER_CREATED 이벤트를 발행하면, 랭킹 ZSET에 weight x price x 수량 만큼 점수가 반영된다.")
    @Test
    void orderEvent_reflectsOrderScore_inRankingZSet() {
        // arrange
        Long productId = 2001L;
        ZonedDateTime occurredAt = ZonedDateTime.now(ZONE);
        OrderEventMessage message = new OrderEventMessage(
            UUID.randomUUID().toString(), "ORDER_CREATED", 999L, 1L,
            List.of(new OrderEventMessage.Item(productId, 2, 10_000L)),
            occurredAt
        );

        // act
        kafkaTemplate.send("order-events", String.valueOf(999L), message);

        // assert
        Double score = awaitScore(rankingKey(occurredAt), productId);
        assertThat(score).isEqualTo(0.6 * 10_000 * 2);
    }

    @DisplayName("PRODUCT_LIKED 이벤트를 발행하면, 랭킹 ZSET에 좋아요 점수가 반영된다.")
    @Test
    void likeEvent_reflectsLikeScore_inRankingZSet() {
        // arrange
        Long productId = 3001L;
        ZonedDateTime occurredAt = ZonedDateTime.now(ZONE);
        LikeEventMessage message = new LikeEventMessage(
            UUID.randomUUID().toString(), "PRODUCT_LIKED", productId, occurredAt
        );

        // act
        kafkaTemplate.send("like-events", String.valueOf(productId), message);

        // assert
        Double score = awaitScore(rankingKey(occurredAt), productId);
        assertThat(score).isEqualTo(0.2);
    }

    @DisplayName("가중치 적용 결과, 주문 1건을 발행한 상품이 좋아요 3건을 발행한 상품보다 랭킹 상위에 위치한다.")
    @Test
    void orderEvent_outranksThreeLikeEvents() {
        // arrange
        Long orderedProduct = 4001L;
        Long likedProduct = 4002L;
        ZonedDateTime occurredAt = ZonedDateTime.now(ZONE);
        String key = rankingKey(occurredAt);

        OrderEventMessage orderMessage = new OrderEventMessage(
            UUID.randomUUID().toString(), "ORDER_CREATED", 999L, 1L,
            List.of(new OrderEventMessage.Item(orderedProduct, 1, 1_000L)),
            occurredAt
        );

        // act
        kafkaTemplate.send("order-events", String.valueOf(999L), orderMessage);
        awaitScore(key, orderedProduct);

        for (int i = 0; i < 3; i++) {
            LikeEventMessage likeMessage = new LikeEventMessage(
                UUID.randomUUID().toString(), "PRODUCT_LIKED", likedProduct, occurredAt
            );
            kafkaTemplate.send("like-events", String.valueOf(likedProduct), likeMessage);
        }
        awaitUntil(
            () -> redisTemplate.opsForZSet().score(key, String.valueOf(likedProduct)),
            score -> score != null && score >= 0.6 - 1e-9
        );

        // assert: 랭킹 순위(reverseRank)는 낮을수록(0에 가까울수록) 상위
        Long orderedRank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(orderedProduct));
        Long likedRank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(likedProduct));

        assertThat(orderedRank).isLessThan(likedRank);
    }
}
