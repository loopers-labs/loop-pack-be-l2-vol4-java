package com.loopers.infrastructure.catalog.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.catalog.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(classes = {
    RedisConfig.class,
    RedisCleanUp.class,
    RedisRankingRepository.class
})
@TestPropertySource(properties = {
    "datasource.redis.database=0",
    "datasource.redis.master.host=localhost",
    "datasource.redis.master.port=6379",
    "datasource.redis.replicas[0].host=localhost",
    "datasource.redis.replicas[0].port=6380"
})
class RedisRankingRepositoryTest {

    private final RankingRepository rankingRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisRankingRepositoryTest(
        RankingRepository rankingRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.rankingRepository = rankingRepository;
        this.redisTemplate = redisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("같은 ranking eventId는 Redis에서 한 번만 점수를 반영한다.")
    @Test
    void incrementsScoresOnlyOnce_whenEventIdIsDuplicated() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 12);
        List<RankingRepository.Score> scores = List.of(
            new RankingRepository.Score(1L, 0.1),
            new RankingRepository.Score(2L, 1.2)
        );

        // act
        boolean first = rankingRepository.incrementScoresOnce(date, "ranking:event-1", scores, Duration.ofDays(2));
        boolean second = rankingRepository.incrementScoresOnce(date, "ranking:event-1", scores, Duration.ofDays(2));

        // assert
        assertAll(
            () -> assertThat(first).isTrue(),
            () -> assertThat(second).isFalse(),
            () -> assertThat(redisTemplate.opsForZSet().score("ranking:all:20260712", "1")).isEqualTo(0.1),
            () -> assertThat(redisTemplate.opsForZSet().score("ranking:all:20260712", "2")).isEqualTo(1.2),
            () -> assertThat(redisTemplate.hasKey("ranking:handled:ranking:event-1")).isTrue()
        );
    }
}
