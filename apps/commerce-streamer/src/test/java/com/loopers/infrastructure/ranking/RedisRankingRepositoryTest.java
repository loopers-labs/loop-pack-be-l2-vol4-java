package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RedisRankingRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @Autowired RankingRepository rankingRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() { redisCleanUp.truncateAll(); }

    @DisplayName("최초 eventId 는 점수를 반영하고 true, 같은 eventId 재시도는 반영 없이 false — SETNX dedup.")
    @Test
    void applyOnceIsIdempotent() {
        boolean first = rankingRepository.applyOnce("evt-1", DATE, Map.of(101L, 0.2));
        boolean second = rankingRepository.applyOnce("evt-1", DATE, Map.of(101L, 0.2));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(rankingRepository.findScore(DATE, 101L)).hasValueSatisfying(
            s -> assertThat(s).isCloseTo(0.2, within(1e-9)));
    }

    @DisplayName("한 이벤트의 여러 상품 delta(주문 라인)는 한 번에 모두 반영된다.")
    @Test
    void applyOnceMultipleProducts() {
        rankingRepository.applyOnce("evt-2", DATE, Map.of(101L, 0.6, 102L, 1.2));

        assertThat(rankingRepository.findScore(DATE, 101L)).hasValueSatisfying(
            s -> assertThat(s).isCloseTo(0.6, within(1e-9)));
        assertThat(rankingRepository.findScore(DATE, 102L)).hasValueSatisfying(
            s -> assertThat(s).isCloseTo(1.2, within(1e-9)));
    }

    @DisplayName("반영 시 일간 키와 dedup 키 모두 TTL 이 설정된다 (2일).")
    @Test
    void ttlIsSet() {
        rankingRepository.applyOnce("evt-3", DATE, Map.of(101L, 0.1));

        Long dailyTtl = redisTemplate.getExpire(RankingKeys.daily(DATE));
        Long dedupTtl = redisTemplate.getExpire(RankingKeys.dedup("evt-3"));
        assertThat(dailyTtl).isGreaterThan(0L).isLessThanOrEqualTo(2 * 24 * 3600L);
        assertThat(dedupTtl).isGreaterThan(0L).isLessThanOrEqualTo(2 * 24 * 3600L);
    }

    @DisplayName("carryOver 는 전일 점수 × rate 를 다음날 키로 복사하고 TTL 을 건다 — 콜드 스타트 완화.")
    @Test
    void carryOverCopiesWeightedScores() {
        rankingRepository.applyOnce("evt-4", DATE, Map.of(101L, 100.0, 102L, 50.0));

        rankingRepository.carryOver(DATE, DATE.plusDays(1), 0.1);

        assertThat(rankingRepository.findScore(DATE.plusDays(1), 101L)).hasValueSatisfying(
            s -> assertThat(s).isCloseTo(10.0, within(1e-9)));
        assertThat(rankingRepository.findScore(DATE.plusDays(1), 102L)).hasValueSatisfying(
            s -> assertThat(s).isCloseTo(5.0, within(1e-9)));
        assertThat(redisTemplate.getExpire(RankingKeys.daily(DATE.plusDays(1)))).isGreaterThan(0L);
    }

    @DisplayName("빈 delta 는 아무 것도 반영하지 않고 false 다.")
    @Test
    void emptyDeltasNoop() {
        assertThat(rankingRepository.applyOnce("evt-5", DATE, Map.of())).isFalse();
        assertThat(redisTemplate.hasKey(RankingKeys.dedup("evt-5"))).isFalse();
    }
}
