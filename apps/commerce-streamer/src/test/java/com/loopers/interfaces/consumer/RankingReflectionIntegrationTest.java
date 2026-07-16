package com.loopers.interfaces.consumer;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingScorePolicy;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RankingReflectionIntegrationTest {

    private static final Acknowledgment NO_OP_ACK = () -> { };

    private final CatalogEventsConsumer catalogEventsConsumer;
    private final OrderEventsConsumer orderEventsConsumer;
    private final RankingScorePolicy rankingScorePolicy;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RankingReflectionIntegrationTest(
        CatalogEventsConsumer catalogEventsConsumer,
        OrderEventsConsumer orderEventsConsumer,
        RankingScorePolicy rankingScorePolicy,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.catalogEventsConsumer = catalogEventsConsumer;
        this.orderEventsConsumer = orderEventsConsumer;
        this.rankingScorePolicy = rankingScorePolicy;
        this.masterRedisTemplate = masterRedisTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("주문 이벤트가 커밋되면 라인 금액 기반 점수가 오늘 랭킹판에 반영된다.")
    @Test
    void reflectsOrderScoreAfterCommit() {
        // given
        long productId = 101L;
        long lineAmount = 10_000L;

        // when
        orderEventsConsumer.consume(List.of(orderRecord("evt-1", 100L, productId, 1, lineAmount)), NO_OP_ACK);

        // then
        double expected = rankingScorePolicy.scoreFor(RankingSignal.ORDER, lineAmount);
        assertThat(todayScore(productId)).isCloseTo(expected, within(1e-9));
    }

    @DisplayName("같은 주문 이벤트를 두 번 받아도 랭킹 점수는 한 번만 반영된다 — 멱등.")
    @Test
    void reflectsOrderScoreOnce_whenSameEventConsumedTwice() {
        // given
        long productId = 101L;
        long lineAmount = 10_000L;

        // when
        orderEventsConsumer.consume(List.of(orderRecord("evt-1", 100L, productId, 1, lineAmount)), NO_OP_ACK);
        orderEventsConsumer.consume(List.of(orderRecord("evt-1", 100L, productId, 1, lineAmount)), NO_OP_ACK);

        // then
        double expected = rankingScorePolicy.scoreFor(RankingSignal.ORDER, lineAmount);
        assertThat(todayScore(productId)).isCloseTo(expected, within(1e-9));
    }

    @DisplayName("좋아요 이벤트가 커밋되면 좋아요 가중치가 오늘 랭킹판에 반영된다.")
    @Test
    void reflectsLikeScoreAfterCommit() {
        // given
        long productId = 1L;

        // when
        catalogEventsConsumer.consume(List.of(catalogRecord("evt-1", "LIKED", productId)), NO_OP_ACK);

        // then
        double expected = rankingScorePolicy.scoreFor(RankingSignal.LIKE, 0);
        assertThat(todayScore(productId)).isCloseTo(expected, within(1e-9));
    }

    @DisplayName("조회 이벤트가 커밋되면 조회 가중치가 오늘 랭킹판에 반영된다.")
    @Test
    void reflectsViewScoreAfterCommit() {
        // given
        long productId = 1L;

        // when
        catalogEventsConsumer.consume(List.of(viewRecord("evt-1", productId)), NO_OP_ACK);

        // then
        double expected = rankingScorePolicy.scoreFor(RankingSignal.VIEW, 0);
        assertThat(todayScore(productId)).isCloseTo(expected, within(1e-9));
    }

    @DisplayName("재고 변경 이벤트는 랭킹 신호가 아니므로 랭킹판에 점수가 생기지 않는다.")
    @Test
    void doesNotReflectStockChanged() {
        // given
        long productId = 1L;

        // when
        catalogEventsConsumer.consume(List.of(stockRecord("evt-1", productId, 50, 5)), NO_OP_ACK);

        // then
        assertThat(todayScore(productId)).isNull();
    }

    private Double todayScore(long productId) {
        String key = RankingKey.of(LocalDate.now()).value();
        return masterRedisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }

    private ConsumerRecord<String, byte[]> orderRecord(String eventId, long orderId, long productId, int quantity, long lineAmount) {
        String json = """
            {"eventId":"%s","eventType":"ORDER_PLACED","aggregateId":%d,"data":{"orderId":%d,"lines":[{"productId":%d,"quantity":%d,"lineAmount":%d}]}}
            """.formatted(eventId, orderId, orderId, productId, quantity, lineAmount);
        return new ConsumerRecord<>("order-events", 0, 0L, String.valueOf(orderId), json.getBytes(StandardCharsets.UTF_8));
    }

    private ConsumerRecord<String, byte[]> catalogRecord(String eventId, String eventType, long productId) {
        String json = """
            {"eventId":"%s","eventType":"%s","aggregateId":%d,"data":{"productId":%d,"type":"%s"}}
            """.formatted(eventId, eventType, productId, productId, eventType);
        return new ConsumerRecord<>("catalog-events", 0, 0L, String.valueOf(productId), json.getBytes(StandardCharsets.UTF_8));
    }

    private ConsumerRecord<String, byte[]> viewRecord(String eventId, long productId) {
        String json = """
            {"eventId":"%s","eventType":"VIEWED","aggregateId":%d,"data":{}}
            """.formatted(eventId, productId);
        return new ConsumerRecord<>("catalog-events", 0, 0L, String.valueOf(productId), json.getBytes(StandardCharsets.UTF_8));
    }

    private ConsumerRecord<String, byte[]> stockRecord(String eventId, long productId, long quantity, long version) {
        String json = """
            {"eventId":"%s","eventType":"STOCK_CHANGED","aggregateId":%d,"data":{"quantity":%d,"version":%d}}
            """.formatted(eventId, productId, quantity, version);
        return new ConsumerRecord<>("catalog-events", 0, 0L, String.valueOf(productId), json.getBytes(StandardCharsets.UTF_8));
    }
}
