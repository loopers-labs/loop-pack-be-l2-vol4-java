package com.loopers.tddstudy.infrastructure.ranking;

import com.loopers.tddstudy.domain.ranking.RankingItem;
import com.loopers.tddstudy.domain.ranking.RankingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataRedisTest
@Import(RankingRedisRepository.class)
class RankingRedisRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 14);
    private static final String KEY = "ranking:all:20260714";

    @Autowired
    RankingRepository rankingRepository;

    @Autowired
    StringRedisTemplate redis;

    @AfterEach
    void tearDown() {
        redis.delete(KEY);   // 테스트 간 랭킹판 오염 방지
    }

    @Test
    @DisplayName("같은 상품에 점수를 더하면 누적된다 (ZINCRBY)")
    void incrementAccumulates() {
        rankingRepository.incrementScore(DATE, 101L, 0.7);
        rankingRepository.incrementScore(DATE, 101L, 0.2);

        Double score = redis.opsForZSet().score(KEY, "101");
        assertThat(score).isCloseTo(0.9, within(1e-9));   // 0.7+0.2 는 double에서 정확히 0.9가 아님
    }

    @Test
    @DisplayName("점수를 넣으면 키에 TTL 2일이 걸린다")
    void ttlIsSet() {
        rankingRepository.incrementScore(DATE, 101L, 0.7);

        Long ttlSeconds = redis.getExpire(KEY);
        assertThat(ttlSeconds).isGreaterThan(0);                    // -1(무제한)이 아님
        assertThat(ttlSeconds).isLessThanOrEqualTo(2 * 24 * 3600L); // 2일 이하
    }

    @Test
    @DisplayName("페이지 조회는 점수 내림차순이다 (ZREVRANGE)")
    void pageIsScoreDescending() {
        rankingRepository.incrementScore(DATE, 101L, 0.2);
        rankingRepository.incrementScore(DATE, 202L, 0.7);
        rankingRepository.incrementScore(DATE, 303L, 0.4);

        List<RankingItem> page = rankingRepository.getPage(DATE, 1, 2);

        assertThat(page).containsExactly(
                new RankingItem(202L, 0.7),
                new RankingItem(303L, 0.4)
        );  // size=2라 101은 잘림
    }

    @Test
    @DisplayName("순위는 0-based, 랭킹판에 없으면 null (ZREVRANK)")
    void rankIsZeroBasedOrNull() {
        rankingRepository.incrementScore(DATE, 101L, 0.2);
        rankingRepository.incrementScore(DATE, 202L, 0.7);

        assertThat(rankingRepository.getRank(DATE, 202L)).isEqualTo(0L);  // 1등
        assertThat(rankingRepository.getRank(DATE, 101L)).isEqualTo(1L);
        assertThat(rankingRepository.getRank(DATE, 999L)).isNull();       // 없는 상품
    }
}
