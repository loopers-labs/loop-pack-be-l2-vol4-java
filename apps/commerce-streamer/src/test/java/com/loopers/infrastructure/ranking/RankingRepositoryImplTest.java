package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class RankingRepositoryImplTest {

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

    @DisplayName("incrementScore는 ZSET에 productId의 score를 누적한다.")
    @Test
    void incrementScore_accumulatesScore() {
        String key = "ranking:all:20260717";

        rankingRepository.incrementScore(key, 10L, 0.1);
        rankingRepository.incrementScore(key, 10L, 0.2);

        Double score = redisTemplate.opsForZSet().score(key, "10");
        assertThat(score).isCloseTo(0.3, within(0.0001));
    }

    @DisplayName("incrementScore 호출 시 key에 2일 TTL이 설정된다.")
    @Test
    void incrementScore_setsTwoDayTtl() {
        String key = "ranking:all:20260717";

        rankingRepository.incrementScore(key, 10L, 0.1);

        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isGreaterThan(Duration.ofDays(1).toSeconds());
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofDays(2).toSeconds());
    }
}
