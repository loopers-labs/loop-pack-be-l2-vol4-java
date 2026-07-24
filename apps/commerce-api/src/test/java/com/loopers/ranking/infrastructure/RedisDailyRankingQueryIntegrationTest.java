package com.loopers.ranking.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.DailyRankingEntries;
import com.loopers.ranking.application.DailyRankingQuery;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
class RedisDailyRankingQueryIntegrationTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 7, 13);

    private final DailyRankingQuery dailyRankingQuery;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisDailyRankingQueryIntegrationTest(
        DailyRankingQuery dailyRankingQuery,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.dailyRankingQuery = dailyRankingQuery;
        this.masterRedisTemplate = masterRedisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("일간 Ranking을 조회할 때")
    @Nested
    class FindDaily {

        @DisplayName("요청 범위의 상품 ID를 점수 내림차순으로 반환하고 전체 상품 수를 함께 제공한다")
        @Test
        void returnsProductIdsInScoreDescendingOrderAndTotalCount() {
            // arrange
            String key = RankingRedisKey.daily(RANKING_DATE);
            masterRedisTemplate.opsForZSet().add(key, "101", 1.0);
            masterRedisTemplate.opsForZSet().add(key, "205", 3.0);
            masterRedisTemplate.opsForZSet().add(key, "309", 2.0);

            // act
            DailyRankingEntries result = dailyRankingQuery.findDaily(RANKING_DATE, 1, 2);

            // assert
            assertAll(
                () -> assertThat(result.productIds()).containsExactly(309L, 101L),
                () -> assertThat(result.totalElements()).isEqualTo(3)
            );
        }

        @DisplayName("Ranking Key가 없으면 빈 상품 ID 목록과 전체 개수 0을 반환한다")
        @Test
        void returnsEmptyResult_whenRankingKeyDoesNotExist() {
            // act
            DailyRankingEntries result = dailyRankingQuery.findDaily(RANKING_DATE, 0, 19);

            // assert
            assertAll(
                () -> assertThat(result.productIds()).isEmpty(),
                () -> assertThat(result.totalElements()).isZero()
            );
        }
    }

    @DisplayName("상품의 일간 Ranking 위치를 조회할 때")
    @Nested
    class FindDailyRank {

        @DisplayName("Score 내림차순 기준의 0-based 위치를 반환한다")
        @Test
        void returnsZeroBasedPosition() {
            // arrange
            String key = RankingRedisKey.daily(RANKING_DATE);
            masterRedisTemplate.opsForZSet().add(key, "101", 1.0);
            masterRedisTemplate.opsForZSet().add(key, "205", 3.0);
            masterRedisTemplate.opsForZSet().add(key, "309", 2.0);

            // act
            Optional<Long> result = dailyRankingQuery.findDailyRank(RANKING_DATE, 309L);

            // assert
            assertThat(result).contains(1L);
        }

        @DisplayName("상품이 Ranking에 없으면 빈 위치를 반환한다")
        @Test
        void returnsEmpty_whenProductIsNotRanked() {
            // act
            Optional<Long> result = dailyRankingQuery.findDailyRank(RANKING_DATE, 999L);

            // assert
            assertThat(result).isEmpty();
        }
    }
}
