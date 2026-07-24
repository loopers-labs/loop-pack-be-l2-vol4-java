package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProductEntry;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingQueryRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingQueryRepositoryImplTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @Autowired RankingQueryRepository rankingQueryRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired RedisCleanUp redisCleanUp;

    @BeforeEach
    void seed() {
        String key = RankingKeys.daily(DATE);
        redisTemplate.opsForZSet().add(key, "101", 3.0);
        redisTemplate.opsForZSet().add(key, "102", 2.0);
        redisTemplate.opsForZSet().add(key, "103", 1.0);
    }

    @AfterEach
    void tearDown() { redisCleanUp.truncateAll(); }

    @DisplayName("findPage 는 점수 내림차순으로 offset/size 만큼 반환한다.")
    @Test
    void findPageDescByScore() {
        List<RankedProductEntry> page1 = rankingQueryRepository.findPage(DATE, 0, 2);
        List<RankedProductEntry> page2 = rankingQueryRepository.findPage(DATE, 2, 2);

        assertThat(page1).extracting(RankedProductEntry::productId).containsExactly(101L, 102L);
        assertThat(page1.get(0).score()).isEqualTo(3.0);
        assertThat(page2).extracting(RankedProductEntry::productId).containsExactly(103L);
    }

    @DisplayName("findRank 는 0-based 순위를, 순위 밖 상품은 empty 를 반환한다.")
    @Test
    void findRank() {
        assertThat(rankingQueryRepository.findRank(DATE, 101L)).hasValue(0L);
        assertThat(rankingQueryRepository.findRank(DATE, 103L)).hasValue(2L);
        assertThat(rankingQueryRepository.findRank(DATE, 999L)).isEmpty();
    }

    @DisplayName("countRanked 는 멤버 수를, 없는 날짜 키는 0/빈 목록을 반환한다.")
    @Test
    void countAndMissingKey() {
        assertThat(rankingQueryRepository.countRanked(DATE)).isEqualTo(3L);
        assertThat(rankingQueryRepository.countRanked(DATE.plusDays(7))).isZero();
        assertThat(rankingQueryRepository.findPage(DATE.plusDays(7), 0, 10)).isEmpty();
    }
}
