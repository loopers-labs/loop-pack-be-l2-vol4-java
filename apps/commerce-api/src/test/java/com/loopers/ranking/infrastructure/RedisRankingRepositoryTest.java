package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RedisRankingRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 17);

    @Autowired
    private RankingRepository rankingRepository;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RedisCleanUp redisCleanUp;

    private String key() {
        return "ranking:all:" + DATE.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @BeforeEach
    void seed() {
        redisTemplate.opsForZSet().add(key(), "101", 5.0);
        redisTemplate.opsForZSet().add(key(), "202", 3.0);
        redisTemplate.opsForZSet().add(key(), "303", 1.0);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("range 는 점수 내림차순으로 [start, end] 구간을 조회한다")
    @Test
    void range_returnsDescByScore() {
        List<RankingEntry> top2 = rankingRepository.range(DATE, 0, 1);

        assertThat(top2).extracting(RankingEntry::productId).containsExactly(101L, 202L);
        assertThat(top2.get(0).score()).isCloseTo(5.0, within(1e-9));
    }

    @DisplayName("size 는 전체 상품 수를 돌려준다")
    @Test
    void size_returnsCardinality() {
        assertThat(rankingRepository.size(DATE)).isEqualTo(3L);
    }

    @DisplayName("rank 는 0-based 순위를 돌려주고, 없으면 null 이다")
    @Test
    void rank_returnsZeroBasedRank_orNull() {
        assertThat(rankingRepository.rank(DATE, 101L)).isEqualTo(0L);
        assertThat(rankingRepository.rank(DATE, 202L)).isEqualTo(1L);
        assertThat(rankingRepository.rank(DATE, 999L)).isNull();
    }
}
