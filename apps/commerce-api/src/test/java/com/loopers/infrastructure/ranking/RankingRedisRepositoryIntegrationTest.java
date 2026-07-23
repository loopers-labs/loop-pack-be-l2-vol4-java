package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 랭킹 조회(읽기) 통합 테스트 — 실제 Redis ZSET으로 ZREVRANGE/ZCARD/ZREVRANK 동작을 검증한다.
 * ⚠️ Testcontainers Redis가 필요하므로 <b>Docker가 떠 있어야</b> 실행된다.
 *
 * <p>시드: 상품 30(9.0) > 10(5.0) > 20(3.0) 내림차순.
 */
@SpringBootTest
class RankingRedisRepositoryIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 14);
    private static final String KEY = RankingKey.daily(DATE); // ranking:all:20260714

    @Autowired RankingRepository rankingRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired RedisCleanUp redisCleanUp;

    @BeforeEach
    void seed() {
        redisTemplate.opsForZSet().add(KEY, "30", 9.0);
        redisTemplate.opsForZSet().add(KEY, "10", 5.0);
        redisTemplate.opsForZSet().add(KEY, "20", 3.0);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("findPage 1페이지는 스코어 내림차순 상위와 1-based 순위를 돌려준다.")
    @Test
    void findPage_firstPage() {
        List<RankedProduct> page = rankingRepository.findPage(DATE, 1, 2);

        assertThat(page).hasSize(2);
        assertThat(page.get(0).rank()).isEqualTo(1);
        assertThat(page.get(0).productId()).isEqualTo(30L);
        assertThat(page.get(0).score()).isCloseTo(9.0, within(1e-9));
        assertThat(page.get(1).rank()).isEqualTo(2);
        assertThat(page.get(1).productId()).isEqualTo(10L);
        assertThat(page.get(1).score()).isCloseTo(5.0, within(1e-9));
    }

    @DisplayName("findPage 2페이지는 offset 이후를 이어서 돌려준다(순위 연속).")
    @Test
    void findPage_secondPage() {
        List<RankedProduct> page = rankingRepository.findPage(DATE, 2, 2);

        assertThat(page).hasSize(1);
        assertThat(page.get(0).rank()).isEqualTo(3);
        assertThat(page.get(0).productId()).isEqualTo(20L);
    }

    @DisplayName("size는 랭킹에 오른 상품 수(ZCARD)를 돌려준다.")
    @Test
    void size() {
        assertThat(rankingRepository.size(DATE)).isEqualTo(3L);
    }

    @DisplayName("findRank는 1-based 순위를, 없으면 empty를 돌려준다.")
    @Test
    void findRank() {
        assertThat(rankingRepository.findRank(DATE, 30L)).contains(1L);
        assertThat(rankingRepository.findRank(DATE, 10L)).contains(2L);
        assertThat(rankingRepository.findRank(DATE, 20L)).contains(3L);
        assertThat(rankingRepository.findRank(DATE, 999L)).isEmpty();
    }

    @DisplayName("데이터가 없는 날짜는 빈 페이지·0·empty를 돌려준다.")
    @Test
    void emptyDate() {
        LocalDate other = LocalDate.of(2020, 1, 1);
        assertThat(rankingRepository.findPage(other, 1, 20)).isEmpty();
        assertThat(rankingRepository.size(other)).isZero();
        assertThat(rankingRepository.findRank(other, 30L)).isEmpty();
    }
}
