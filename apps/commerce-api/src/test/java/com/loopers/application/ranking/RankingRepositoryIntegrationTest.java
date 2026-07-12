package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.infrastructure.ranking.RankingKeys;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// commerce-api 는 랭킹을 읽기만 하므로(쓰기는 commerce-streamer), 테스트 데이터는
// RedisTemplate 을 직접 주입받아 시딩한다 — 다른 도메인 리포지토리 테스트처럼 도메인 인터페이스만으로는
// 쓰기 경로가 없기 때문.
@SpringBootTest
class RankingRepositoryIntegrationTest {

    @Autowired
    private RankingRepository rankingRepository;
    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void seedScore(LocalDate date, long productId, double score) {
        redisTemplate.opsForZSet().add(RankingKeys.of(date), String.valueOf(productId), score);
    }

    @DisplayName("페이지 단위로 상품 ID를 조회하면, ")
    @Nested
    class FindProductIds {

        @DisplayName("점수 높은 순으로 페이지 크기만큼 반환한다.")
        @Test
        void returnsProductIdsInScoreDescendingOrder() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            seedScore(date, 101L, 30.0);
            seedScore(date, 102L, 50.0);
            seedScore(date, 103L, 10.0);

            // act
            List<Long> result = rankingRepository.findProductIds(date, 1, 2);

            // assert
            assertThat(result).containsExactly(102L, 101L);
        }

        @DisplayName("해당 날짜에 랭킹 데이터가 없으면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenNoDataForDate() {
            // act
            List<Long> result = rankingRepository.findProductIds(LocalDate.of(2099, 1, 1), 1, 20);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("개별 상품 순위를 조회하면, ")
    @Nested
    class FindRank {

        @DisplayName("점수 기준 0부터 시작하는 순위를 반환한다.")
        @Test
        void returnsZeroIndexedRank() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            seedScore(date, 101L, 30.0);
            seedScore(date, 102L, 50.0);

            // assert
            assertThat(rankingRepository.findRank(date, 102L)).contains(0L);
            assertThat(rankingRepository.findRank(date, 101L)).contains(1L);
        }

        @DisplayName("오늘 랭킹에 없는 상품이면, 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenProductNotRanked() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            seedScore(date, 101L, 30.0);

            // act
            Optional<Long> result = rankingRepository.findRank(date, 999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("랭킹 전체 상품 수를 세면, ")
    @Nested
    class Count {

        @DisplayName("해당 날짜 키에 들어있는 상품 개수를 반환한다.")
        @Test
        void returnsMemberCount() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            seedScore(date, 101L, 30.0);
            seedScore(date, 102L, 50.0);
            seedScore(date, 103L, 10.0);

            // assert
            assertThat(rankingRepository.count(date)).isEqualTo(3L);
        }
    }
}
