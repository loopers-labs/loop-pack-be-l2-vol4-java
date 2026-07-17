package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingRepositoryImplTest {

    private static final String KEY = "ranking:all:20260717";

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("getTopN은 score 내림차순으로 상위 N개를 반환한다.")
    @Test
    void getTopN_returnsDescendingByScore() {
        redisTemplate.opsForZSet().add(KEY, "10", 5.0);
        redisTemplate.opsForZSet().add(KEY, "20", 9.0);
        redisTemplate.opsForZSet().add(KEY, "30", 1.0);

        List<RankingRepository.RankingEntry> top = rankingRepository.getTopN(KEY, 0, 2);

        assertThat(top).extracting(RankingRepository.RankingEntry::productId)
            .containsExactly(20L, 10L);
    }

    @DisplayName("getRank는 0부터 시작하는 순위를 반환한다.")
    @Test
    void getRank_returnsZeroBasedRank() {
        redisTemplate.opsForZSet().add(KEY, "10", 5.0);
        redisTemplate.opsForZSet().add(KEY, "20", 9.0);

        Optional<Long> rank = rankingRepository.getRank(KEY, 10L);

        assertThat(rank).contains(1L);
    }

    @DisplayName("랭킹에 없는 상품의 getRank는 빈 값을 반환한다.")
    @Test
    void getRank_returnsEmpty_whenProductNotRanked() {
        Optional<Long> rank = rankingRepository.getRank(KEY, 999L);

        assertThat(rank).isEmpty();
    }

    @DisplayName("getTotalCount는 ZSET의 전체 멤버 수를 반환한다.")
    @Test
    void getTotalCount_returnsZCard() {
        redisTemplate.opsForZSet().add(KEY, "10", 5.0);
        redisTemplate.opsForZSet().add(KEY, "20", 9.0);

        assertThat(rankingRepository.getTotalCount(KEY)).isEqualTo(2L);
    }
}
