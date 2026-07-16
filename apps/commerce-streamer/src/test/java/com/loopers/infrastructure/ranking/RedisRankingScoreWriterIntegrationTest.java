package com.loopers.infrastructure.ranking;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RedisRankingScoreWriterIntegrationTest {
    private final RedisRankingScoreWriter writer;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisRankingScoreWriterIntegrationTest(
        RedisRankingScoreWriter writer,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.writer = writer;
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

    @DisplayName("누적 점수가 0 이하가 되면 랭킹에서 제거한다.")
    @Test
    void removesProductWhenScoreIsNotPositive() {
        String key = "ranking:all:20260717";
        writer.increment(key, 1L, 0.2);

        writer.increment(key, 1L, -0.2);

        assertThat(redisTemplate.opsForZSet().score(key, "1")).isNull();
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
}
