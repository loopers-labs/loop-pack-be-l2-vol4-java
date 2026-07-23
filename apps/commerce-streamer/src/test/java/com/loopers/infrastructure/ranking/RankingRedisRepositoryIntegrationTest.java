package com.loopers.infrastructure.ranking;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 랭킹 적재(쓰기) 통합 테스트 — 실제 Redis ZSET으로 ZINCRBY 누적 + TTL 설정을 검증한다.
 * ⚠️ Testcontainers Redis가 필요하므로 <b>Docker가 떠 있어야</b> 실행된다.
 */
@SpringBootTest
class RankingRedisRepositoryIntegrationTest {

    private static final String KEY = "ranking:all:20260714";
    private static final long TTL_SECONDS = 2 * 24 * 60 * 60; // 2d

    @Autowired RankingRedisRepository rankingRedisRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("incrementAll은 상품별 델타를 ZINCRBY로 반영하고 키에 2일 TTL을 건다.")
    @Test
    void incrementAll_writesScoresAndTtl() {
        rankingRedisRepository.incrementAll(Map.of(KEY, Map.of("10", 0.4, "20", -0.2)));

        assertThat(redisTemplate.opsForZSet().score(KEY, "10")).isCloseTo(0.4, within(1e-9));
        assertThat(redisTemplate.opsForZSet().score(KEY, "20")).isCloseTo(-0.2, within(1e-9));

        Long ttl = redisTemplate.getExpire(KEY); // 초 단위 잔여 TTL
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(TTL_SECONDS - 60, TTL_SECONDS); // ~2d
    }

    @DisplayName("같은 member에 대한 반복 incrementAll은 ZINCRBY로 누적된다.")
    @Test
    void incrementAll_accumulates() {
        rankingRedisRepository.incrementAll(Map.of(KEY, Map.of("10", 0.1)));
        rankingRedisRepository.incrementAll(Map.of(KEY, Map.of("10", 0.3)));

        assertThat(redisTemplate.opsForZSet().score(KEY, "10")).isCloseTo(0.4, within(1e-9));
    }

    @DisplayName("델타가 0인 member는 쓰지 않는다(불필요한 ZINCRBY 회피).")
    @Test
    void incrementAll_skipsZeroDelta() {
        rankingRedisRepository.incrementAll(Map.of(KEY, Map.of("10", 0.0)));

        assertThat(redisTemplate.opsForZSet().score(KEY, "10")).isNull(); // 미기록
    }

    @DisplayName("빈 입력은 아무것도 쓰지 않는다.")
    @Test
    void incrementAll_empty() {
        rankingRedisRepository.incrementAll(Map.of());

        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }
}
