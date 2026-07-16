package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.Rank;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisRankingRepositoryIntegrationTest {

    private static final RankingKey KEY = RankingKey.of(LocalDate.of(2025, 9, 6));

    private final RankingRepository rankingRepository;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisRankingRepositoryIntegrationTest(
        RankingRepository rankingRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.rankingRepository = rankingRepository;
        this.masterRedisTemplate = masterRedisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void seed(long productId, double score) {
        masterRedisTemplate.opsForZSet().add(KEY.value(), String.valueOf(productId), score);
    }

    @DisplayName("상위 N 조회는 점수 내림차순으로 (상품, 점수) 를 반환한다.")
    @Test
    void topNReturnsEntriesInDescendingScore() {
        // given
        seed(101L, 1.0);
        seed(102L, 3.0);
        seed(103L, 2.0);

        // when
        List<RankingEntry> top = rankingRepository.topN(KEY, 0, 10);

        // then
        assertThat(top).extracting(RankingEntry::productId).containsExactly(102L, 103L, 101L);
        assertThat(top).extracting(RankingEntry::score).containsExactly(3.0, 2.0, 1.0);
    }

    @DisplayName("페이지와 크기로 상위 구간을 잘라 조회한다.")
    @Test
    void topNPagesBySizeAndPage() {
        // given
        seed(101L, 5.0);
        seed(102L, 4.0);
        seed(103L, 3.0);
        seed(104L, 2.0);
        seed(105L, 1.0);

        // when
        List<RankingEntry> firstPage = rankingRepository.topN(KEY, 0, 2);
        List<RankingEntry> secondPage = rankingRepository.topN(KEY, 1, 2);

        // then
        assertThat(firstPage).extracting(RankingEntry::productId).containsExactly(101L, 102L);
        assertThat(secondPage).extracting(RankingEntry::productId).containsExactly(103L, 104L);
    }

    @DisplayName("랭킹에 든 상품의 순위는 1-based 로 반환된다.")
    @Test
    void rankOfReturnsOneBasedRank() {
        // given
        seed(101L, 3.0);   // 1위
        seed(102L, 2.0);   // 2위
        seed(103L, 1.0);   // 3위

        // when
        Optional<Rank> topRank = rankingRepository.rankOf(KEY, 101L);
        Optional<Rank> lastRank = rankingRepository.rankOf(KEY, 103L);

        // then
        assertThat(topRank).map(Rank::value).contains(1L);
        assertThat(lastRank).map(Rank::value).contains(3L);
    }

    @DisplayName("랭킹 밖 상품의 순위 조회는 빈 결과를 준다.")
    @Test
    void rankOfReturnsEmptyForProductNotRanked() {
        // given
        seed(101L, 3.0);

        // when
        Optional<Rank> rank = rankingRepository.rankOf(KEY, 999L);

        // then
        assertThat(rank).isEmpty();
    }

    @DisplayName("랭킹판 크기는 진입한 상품 수와 같다.")
    @Test
    void sizeReturnsNumberOfRankedProducts() {
        // given
        seed(101L, 3.0);
        seed(102L, 2.0);

        // when
        long size = rankingRepository.size(KEY);

        // then
        assertThat(size).isEqualTo(2L);
    }
}
