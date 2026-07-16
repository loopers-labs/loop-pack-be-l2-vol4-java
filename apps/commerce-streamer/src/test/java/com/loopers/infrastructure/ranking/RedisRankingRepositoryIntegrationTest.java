package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisRankingRepositoryIntegrationTest {

    private static final RankingKey KEY = RankingKey.of(LocalDate.of(2025, 9, 6));
    private static final long PRODUCT_ID = 101L;

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

    @DisplayName("같은 상품에 점수를 두 번 반영하면 델타가 누적된다.")
    @Test
    void accumulatesDeltaOnRepeatedIncrements() {
        // given
        rankingRepository.incrementScore(KEY, PRODUCT_ID, 0.1);

        // when
        rankingRepository.incrementScore(KEY, PRODUCT_ID, 0.7);

        // then
        Double score = masterRedisTemplate.opsForZSet().score(KEY.value(), String.valueOf(PRODUCT_ID));
        assertThat(score).isCloseTo(0.8, within(1e-9));
    }

    @DisplayName("최초 쓰기 시 랭킹 키에 2일 이내의 TTL 이 설정된다.")
    @Test
    void setsTwoDayTtlOnFirstWrite() {
        // given
        rankingRepository.incrementScore(KEY, PRODUCT_ID, 0.1);

        // when
        Long ttlSeconds = masterRedisTemplate.getExpire(KEY.value(), TimeUnit.SECONDS);

        // then
        assertThat(ttlSeconds).isNotNull().isPositive();
        assertThat(ttlSeconds).isLessThanOrEqualTo(TimeUnit.DAYS.toSeconds(2));
    }

    @DisplayName("서로 다른 상품의 점수는 독립적으로 누적된다.")
    @Test
    void keepsScoresPerProductIndependent() {
        // given
        rankingRepository.incrementScore(KEY, 101L, 0.1);
        rankingRepository.incrementScore(KEY, 202L, 0.7);

        // when
        Double first = masterRedisTemplate.opsForZSet().score(KEY.value(), "101");
        Double second = masterRedisTemplate.opsForZSet().score(KEY.value(), "202");

        // then
        assertThat(first).isCloseTo(0.1, within(1e-9));
        assertThat(second).isCloseTo(0.7, within(1e-9));
    }
}
