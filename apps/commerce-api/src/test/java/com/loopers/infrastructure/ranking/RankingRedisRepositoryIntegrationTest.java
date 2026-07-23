package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "queue.scheduler.enabled=false")
class RankingRedisRepositoryIntegrationTest {

    @Autowired
    private RankingRedisRepository rankingRedisRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);
    private static final String KEY = "ranking:all:20260715";

    @BeforeEach
    void setUp() {
        // 상품 1~4는 양수 점수, 상품 5는 0점, 상품 6은 음수(크로스데이 감점 유령 멤버)
        redisTemplate.opsForZSet().add(KEY, "1", 6_000.0);
        redisTemplate.opsForZSet().add(KEY, "2", 0.6);
        redisTemplate.opsForZSet().add(KEY, "3", 0.3);
        redisTemplate.opsForZSet().add(KEY, "4", 0.1);
        redisTemplate.opsForZSet().add(KEY, "5", 0.0);
        redisTemplate.opsForZSet().add(KEY, "6", -0.2);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("findTopProductIds()를 호출할 때,")
    @Nested
    class FindTopProductIds {

        @DisplayName("양수 점수 상품만 점수 내림차순으로 반환된다 (0점·음수 유령 멤버 제외).")
        @Test
        void returnsOnlyPositiveScored_inDescendingOrder() {
            // act
            List<Long> result = rankingRedisRepository.findTopProductIds(DATE, 0, 20);

            // assert
            assertThat(result).containsExactly(1L, 2L, 3L, 4L);
        }

        @DisplayName("offset과 limit으로 페이지 단위 조회가 된다.")
        @Test
        void paginatesWithOffsetAndLimit() {
            // act — 2페이지 (size=2)
            List<Long> result = rankingRedisRepository.findTopProductIds(DATE, 2, 2);

            // assert
            assertThat(result).containsExactly(3L, 4L);
        }

        @DisplayName("존재하지 않는 날짜 키를 조회하면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenKeyDoesNotExist() {
            // act
            List<Long> result = rankingRedisRepository.findTopProductIds(DATE.plusDays(10), 0, 20);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("countRanked()를 호출할 때,")
    @Nested
    class CountRanked {

        @DisplayName("양수 점수 상품 수만 센다.")
        @Test
        void countsOnlyPositiveScored() {
            // act & assert
            assertThat(rankingRedisRepository.countRanked(DATE)).isEqualTo(4);
        }

        @DisplayName("존재하지 않는 날짜 키는 0을 반환한다.")
        @Test
        void returnsZero_whenKeyDoesNotExist() {
            // act & assert
            assertThat(rankingRedisRepository.countRanked(DATE.plusDays(10))).isZero();
        }
    }

    @DisplayName("findRank()를 호출할 때,")
    @Nested
    class FindRank {

        @DisplayName("점수 내림차순 기준 1-based 순위가 반환된다.")
        @Test
        void returnsOneBasedRank() {
            // act & assert
            assertThat(rankingRedisRepository.findRank(DATE, 1L)).contains(1L);
            assertThat(rankingRedisRepository.findRank(DATE, 3L)).contains(3L);
        }

        @DisplayName("랭킹판에 없는 상품은 empty가 반환된다.")
        @Test
        void returnsEmpty_whenProductIsNotInRanking() {
            // act & assert
            assertThat(rankingRedisRepository.findRank(DATE, 999L)).isEmpty();
        }

        @DisplayName("0점 이하 유령 멤버는 순위 없음(empty)으로 처리된다.")
        @Test
        void returnsEmpty_whenScoreIsZeroOrNegative() {
            // act & assert
            assertThat(rankingRedisRepository.findRank(DATE, 5L)).isEmpty();
            assertThat(rankingRedisRepository.findRank(DATE, 6L)).isEmpty();
        }
    }
}
