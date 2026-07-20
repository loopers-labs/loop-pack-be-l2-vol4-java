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

    @DisplayName("날짜별 랭킹 key에 점수를 누적하고 score 내림차순으로 1-based rank를 조회한다.")
    @Test
    void incrementsScoresAndReturnsRankingsByScoreDesc() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 12);
        Duration ttl = Duration.ofDays(2);
        rankingRepository.incrementScore(date, 1L, 1.0, ttl);
        rankingRepository.incrementScore(date, 2L, 3.0, ttl);
        rankingRepository.incrementScore(date, 3L, 2.0, ttl);

        // act
        List<RankingRepository.Entry> rankings = rankingRepository.findRankings(date, 0, 2);

        // assert
        assertAll(
            () -> assertThat(rankings).extracting(RankingRepository.Entry::productId)
                .containsExactly(2L, 3L),
            () -> assertThat(rankings).extracting(RankingRepository.Entry::rank)
                .containsExactly(1L, 2L),
            () -> assertThat(rankings).extracting(RankingRepository.Entry::score)
                .containsExactly(3.0, 2.0),
            () -> assertThat(rankingRepository.findRank(date, 1L))
                .contains(new RankingRepository.Entry(1L, 3L, 1.0)),
            () -> assertThat(rankingRepository.count(date)).isEqualTo(3L)
        );
    }

    @DisplayName("랭킹 key는 2일 TTL을 가지고 날짜별로 분리된다.")
    @Test
    void keepsRankingKeysSeparatedByDateWithTtl() {
        // arrange
        LocalDate today = LocalDate.of(2026, 7, 12);
        LocalDate tomorrow = today.plusDays(1);
        Duration ttl = Duration.ofDays(2);

        // act
        rankingRepository.incrementScore(today, 1L, 1.0, ttl);
        rankingRepository.incrementScore(tomorrow, 1L, 5.0, ttl);

        // assert
        assertAll(
            () -> assertThat(rankingRepository.findRank(today, 1L))
                .contains(new RankingRepository.Entry(1L, 1L, 1.0)),
            () -> assertThat(rankingRepository.findRank(tomorrow, 1L))
                .contains(new RankingRepository.Entry(1L, 1L, 5.0)),
            () -> assertThat(redisTemplate.getExpire("ranking:all:20260712"))
                .isBetween(1L, ttl.toSeconds()),
            () -> assertThat(redisTemplate.getExpire("ranking:all:20260713"))
                .isBetween(1L, ttl.toSeconds())
        );
    }
}
