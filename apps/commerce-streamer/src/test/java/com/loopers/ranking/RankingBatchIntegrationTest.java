package com.loopers.ranking;

import com.loopers.confg.kafka.message.EventEnvelope;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisStore;
import com.loopers.interfaces.consumer.CommerceEventConsumer;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 배치 컨슈머 → DB 집계 + 랭킹 ZSET 반영 통합 테스트.
 *
 * <p>Kafka 브로커 없이 리스너를 끄고({@code auto-startup=false}) 컨슈머 메서드를 직접 호출해
 * 한 배치 처리의 결과(product_metrics, ranking ZSET, 가중치 반영, 멱등)를 검증한다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class RankingBatchIntegrationTest {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Autowired private CommerceEventConsumer consumer;
    @Autowired private RankingRedisStore rankingRedisStore;
    @Autowired private ProductMetricsJpaRepository productMetricsJpaRepository;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String allKey() {
        return "ranking:all:" + LocalDate.now().format(DAY_FMT);
    }

    private String hourKey() {
        return "ranking:hour:" + LocalDateTime.now().format(HOUR_FMT);
    }

    private void consume(List<EventEnvelope> messages) {
        consumer.onCommerceEvents(messages, () -> { /* no-op ack */ });
    }

    private EventEnvelope view(long productId) {
        return EventEnvelope.of("PRODUCT_VIEWED", Map.of("productId", productId));
    }

    private EventEnvelope like(long productId, String type) {
        return EventEnvelope.of("LIKE_CHANGED", Map.of("productId", productId, "type", type));
    }

    private EventEnvelope order(long productId, long price, long quantity) {
        return EventEnvelope.of("PAYMENT_COMPLETED", Map.of(
            "orderId", 1L,
            "amount", price * quantity,
            "items", List.of(Map.of("productId", productId, "quantity", quantity, "price", price))
        ));
    }

    @DisplayName("한 배치의 조회/좋아요/주문을 합산해 ZSET 점수와 product_metrics 에 정확히 반영한다")
    @Test
    void batchReflectsWeightedScoreAndMetrics() {
        long productId = 100L;
        // 조회 3, 좋아요 +2, 주문 price=1000 qty=2 (log10(2001))
        consume(List.of(
            view(productId), view(productId), view(productId),
            like(productId, "LIKED"), like(productId, "LIKED"),
            order(productId, 1000L, 2L)
        ));

        double orderScore = Math.log10(1000.0 * 2 + 1);
        double expected = 0.1 * 3 + 0.2 * 2 + 0.6 * orderScore;

        Double allScore = redisTemplate.opsForZSet().score(allKey(), String.valueOf(productId));
        Double hourScore = redisTemplate.opsForZSet().score(hourKey(), String.valueOf(productId));
        assertThat(allScore).isNotNull().isCloseTo(expected, within(1e-9));
        assertThat(hourScore).isNotNull().isCloseTo(expected, within(1e-9));

        ProductMetrics metrics = productMetricsJpaRepository.findById(productId).orElseThrow();
        assertThat(metrics.getViewCount()).isEqualTo(3);
        assertThat(metrics.getLikeCount()).isEqualTo(2);
        assertThat(metrics.getSaleCount()).isEqualTo(2);

        // TTL 이 설정되어 있어야 한다(일간 48h, 시간별 3h).
        assertThat(redisTemplate.getExpire(allKey())).isGreaterThan(0);
        assertThat(redisTemplate.getExpire(hourKey())).isGreaterThan(0);
    }

    @DisplayName("TTL 은 최초 쓰기 시점에만 설정되고, 이후 배치가 다시 갱신해도 슬라이딩되지 않는다")
    @Test
    void ttlIsSetOnceNotSlidAcrossBatches() {
        long productId = 500L;
        consume(List.of(view(productId)));
        long ttlAfterFirst = redisTemplate.getExpire(allKey());
        assertThat(ttlAfterFirst).isGreaterThan(0);

        // 키가 이미 TTL 을 가진 상태에서 짧게 줄여둔다 — 슬라이딩이라면 다음 배치가 다시 48h 로 되돌릴 것이다.
        redisTemplate.expire(allKey(), java.time.Duration.ofSeconds(10));

        consume(List.of(view(productId)));   // 같은 키를 다시 건드리는 배치
        long ttlAfterSecond = redisTemplate.getExpire(allKey());

        assertThat(ttlAfterSecond).isGreaterThan(0).isLessThanOrEqualTo(10);   // 48h 로 되돌아가지 않았어야 한다
    }

    @DisplayName("가중치를 바꾸면 다음 배치부터 즉시 반영된다(캐싱하지 않음)")
    @Test
    void weightChangeAppliesToNextBatch() {
        long productId = 200L;
        // 1차 배치 — 기본 가중치 시딩(view=0.1)
        consume(List.of(view(productId)));
        double afterFirst = redisTemplate.opsForZSet().score(allKey(), String.valueOf(productId));
        assertThat(afterFirst).isCloseTo(0.1, within(1e-9));

        // 가중치 view=1.0 으로 변경
        redisTemplate.opsForHash().put("ranking:weights", "view", "1.0");

        // 2차 배치 — 조회 1건 → +1.0 증분
        consume(List.of(view(productId)));
        double afterSecond = redisTemplate.opsForZSet().score(allKey(), String.valueOf(productId));
        assertThat(afterSecond).isCloseTo(0.1 + 1.0, within(1e-9));
    }

    @DisplayName("같은 배치를 재전달해도 멱등 가드로 중복 집계되지 않는다")
    @Test
    void redeliveredBatchIsIdempotent() {
        long productId = 300L;
        List<EventEnvelope> batch = List.of(view(productId), like(productId, "LIKED"));
        consume(batch);
        consume(batch);   // 동일 eventId 재전달

        double score = redisTemplate.opsForZSet().score(allKey(), String.valueOf(productId));
        assertThat(score).isCloseTo(0.1 * 1 + 0.2 * 1, within(1e-9));
        ProductMetrics metrics = productMetricsJpaRepository.findById(productId).orElseThrow();
        assertThat(metrics.getViewCount()).isEqualTo(1);
        assertThat(metrics.getLikeCount()).isEqualTo(1);
    }

    @DisplayName("배치 내 메시지 하나가 깨져있어도(필드 누락) 나머지 메시지는 정상 집계된다 — 포이즌 필 방지")
    @Test
    void malformedMessageDoesNotFailWholeBatch() {
        long healthyProductId = 900L;
        EventEnvelope malformedOrder = EventEnvelope.of("PAYMENT_COMPLETED", Map.of(
            "orderId", 2L,
            "amount", 0L,
            "items", List.of(Map.of("productId", 901L, "quantity", 1L))   // price 필드 누락 — asLong(null) NPE 유발
        ));

        consume(List.of(malformedOrder, view(healthyProductId)));

        // 배치 전체가 롤백되지 않고, 정상 메시지(조회)는 그대로 반영돼야 한다.
        Double score = redisTemplate.opsForZSet().score(allKey(), String.valueOf(healthyProductId));
        assertThat(score).isNotNull().isCloseTo(0.1, within(1e-9));
        ProductMetrics metrics = productMetricsJpaRepository.findById(healthyProductId).orElseThrow();
        assertThat(metrics.getViewCount()).isEqualTo(1);
    }

    @DisplayName("가중치 적용 순서 — 주문 1건이 좋아요 3건보다 랭킹 점수가 높다")
    @Test
    void orderBeatsThreeLikesInRankingOrder() {
        long likedProductId = 400L;
        long orderedProductId = 401L;
        consume(List.of(
            like(likedProductId, "LIKED"), like(likedProductId, "LIKED"), like(likedProductId, "LIKED"),
            order(orderedProductId, 10_000L, 1L)
        ));

        Double likedScore = redisTemplate.opsForZSet().score(allKey(), String.valueOf(likedProductId));
        Double orderedScore = redisTemplate.opsForZSet().score(allKey(), String.valueOf(orderedProductId));

        assertThat(likedScore).isNotNull();
        assertThat(orderedScore).isNotNull();
        assertThat(orderedScore).isGreaterThan(likedScore);

        Long orderedRank = redisTemplate.opsForZSet().reverseRank(allKey(), String.valueOf(orderedProductId));
        Long likedRank = redisTemplate.opsForZSet().reverseRank(allKey(), String.valueOf(likedProductId));
        assertThat(orderedRank).isLessThan(likedRank);   // 0-indexed, 작을수록 더 높은 순위
    }

    @DisplayName("콜드스타트 이월 — 다음 날 키를 오늘 점수의 10% 로 시딩하고 TTL 을 건다")
    @Test
    void carryOverSeedsTomorrowWithTenPercent() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = "ranking:all:" + today.format(DAY_FMT);
        String tomorrowKey = "ranking:all:" + tomorrow.format(DAY_FMT);
        redisTemplate.opsForZSet().add(todayKey, "1", 100.0);
        redisTemplate.opsForZSet().add(todayKey, "2", 50.0);

        rankingRedisStore.carryOver(today, tomorrow);

        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "1")).isCloseTo(10.0, within(1e-9));
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "2")).isCloseTo(5.0, within(1e-9));
        assertThat(redisTemplate.getExpire(tomorrowKey)).isGreaterThan(0);
    }
}
