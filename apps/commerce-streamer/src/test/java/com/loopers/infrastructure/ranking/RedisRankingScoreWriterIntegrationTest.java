package com.loopers.infrastructure.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.ranking.CatalogRankingEventProcessor;
import com.loopers.config.redis.RedisConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RedisRankingScoreWriterIntegrationTest {
    private final RedisRankingScoreWriter writer;
    private final CatalogRankingEventProcessor processor;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisRankingScoreWriterIntegrationTest(
        RedisRankingScoreWriter writer,
        CatalogRankingEventProcessor processor,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.writer = writer;
        this.processor = processor;
        this.redisTemplate = redisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("점수를 누적하고 일간 랭킹 키 TTL을 2일로 갱신한다.")
    @Test
    void incrementsScoreAndSetsTtl() {
        // arrange
        String key = "ranking:all:20260717";

        // act
        writer.increment(key, 1L, 0.2);
        writer.increment(key, 1L, 0.7);

        // assert
        assertThat(redisTemplate.opsForZSet().score(key, "1")).isCloseTo(0.9, within(0.000_001));
        Long ttlSeconds = redisTemplate.getExpire(key);
        assertThat(ttlSeconds).isBetween(2L * 24 * 60 * 60 - 10, 2L * 24 * 60 * 60);
    }

    @DisplayName("누적 점수가 0 이하가 되어도 이후 이벤트 합산을 위해 원점수를 유지한다.")
    @Test
    void retainsRawScoreWhenScoreIsNotPositive() {
        String key = "ranking:all:20260717";
        writer.increment(key, 1L, 0.2);

        writer.increment(key, 1L, -0.2);
        writer.increment(key, 2L, -0.2);

        assertThat(redisTemplate.opsForZSet().score(key, "1")).isZero();
        assertThat(redisTemplate.opsForZSet().score(key, "2")).isCloseTo(-0.2, within(0.000_001));
    }

    @DisplayName("기존 키를 다시 갱신하면 TTL을 2일로 새로 설정한다.")
    @Test
    void refreshesTtlOnEveryUpdate() {
        String key = "ranking:all:20260717";
        writer.increment(key, 1L, 0.2);
        redisTemplate.expire(key, Duration.ofSeconds(30));

        writer.increment(key, 1L, 0.1);

        assertThat(redisTemplate.getExpire(key))
            .isBetween(2L * 24 * 60 * 60 - 10, 2L * 24 * 60 * 60);
    }

    @DisplayName("동일한 이벤트 집합은 Kafka poll 분할과 무관하게 원점수 합계가 같아야 한다.")
    @Test
    void keepsRawScoreDeterministicAcrossKafkaPollBoundaries() {
        String key = "ranking:all:20260717";
        CatalogEventMessage unlikeInSinglePoll = event("PRODUCT_UNLIKED", 1L, Map.of("likeCountDelta", -1));
        CatalogEventMessage viewInSinglePoll = event("PRODUCT_VIEWED", 1L, Map.of("viewCountDelta", 1));
        CatalogEventMessage unlikeInSplitPoll = event("PRODUCT_UNLIKED", 2L, Map.of("likeCountDelta", -1));
        CatalogEventMessage viewInSplitPoll = event("PRODUCT_VIEWED", 2L, Map.of("viewCountDelta", 1));

        processor.process(List.of(unlikeInSinglePoll, viewInSinglePoll));
        processor.process(List.of(unlikeInSplitPoll));
        processor.process(List.of(viewInSplitPoll));

        Double onePollScore = redisTemplate.opsForZSet().score(key, "1");
        Double splitPollScore = redisTemplate.opsForZSet().score(key, "2");

        assertThat(onePollScore)
            .as("한 poll에서 처리한 원점수")
            .isCloseTo(-0.1, within(0.000_001));
        assertThat(splitPollScore)
            .as("여러 poll로 나눠 처리한 원점수")
            .isCloseTo(-0.1, within(0.000_001));
    }

    @DisplayName("최종 합계가 0인 이벤트 집합도 Kafka poll 분할과 무관하게 0점 member를 유지한다.")
    @Test
    void keepsZeroScoreMemberDeterministicAcrossKafkaPollBoundaries() {
        String key = "ranking:all:20260717";
        CatalogEventMessage likeInSinglePoll = event("PRODUCT_LIKED", 1L, Map.of("likeCountDelta", 1));
        CatalogEventMessage unlikeInSinglePoll = event("PRODUCT_UNLIKED", 1L, Map.of("likeCountDelta", -1));
        CatalogEventMessage likeInSplitPoll = event("PRODUCT_LIKED", 2L, Map.of("likeCountDelta", 1));
        CatalogEventMessage unlikeInSplitPoll = event("PRODUCT_UNLIKED", 2L, Map.of("likeCountDelta", -1));

        processor.process(List.of(likeInSinglePoll, unlikeInSinglePoll));
        processor.process(List.of(likeInSplitPoll));
        processor.process(List.of(unlikeInSplitPoll));

        Double onePollScore = redisTemplate.opsForZSet().score(key, "1");
        Double splitPollScore = redisTemplate.opsForZSet().score(key, "2");

        assertThat(onePollScore)
            .as("한 poll에서 처리한 0점 member")
            .isCloseTo(0.0, within(0.000_001));
        assertThat(splitPollScore)
            .as("여러 poll로 나눠 처리한 0점 member")
            .isCloseTo(0.0, within(0.000_001));
    }

    private CatalogEventMessage event(String type, Long productId, Map<String, Object> data) {
        return new CatalogEventMessage(
            "event-" + type + "-" + productId,
            type,
            "PRODUCT",
            productId,
            ZonedDateTime.parse("2026-07-17T09:00:00+09:00"),
            data
        );
    }
}
