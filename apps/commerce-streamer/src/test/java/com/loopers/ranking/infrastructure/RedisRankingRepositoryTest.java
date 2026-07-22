package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.domain.RankingScoreDelta;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RedisRankingRepositoryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long TWO_DAYS_SECONDS = 2 * 24 * 60 * 60L;

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

    private String keyOf(LocalDate date) {
        return "ranking:all:" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @DisplayName("delta 를 ZINCRBY 로 반영하고 키에 절대 만료(≈2일)를 건다")
    @Test
    void incrBy_appliesScores_andSetsTtl() {
        // Arrange
        LocalDate today = LocalDate.now(SEOUL);

        // Act
        rankingRepository.incrBy(List.of(
                new RankingScoreDelta(today, 101L, 1.3),
                new RankingScoreDelta(today, 202L, 0.5)
        ));

        // Assert
        String key = keyOf(today);
        assertThat(redisTemplate.opsForZSet().score(key, "101")).isCloseTo(1.3, within(1e-9));
        assertThat(redisTemplate.opsForZSet().score(key, "202")).isCloseTo(0.5, within(1e-9));
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, TWO_DAYS_SECONDS);
    }

    @DisplayName("같은 member 를 다시 반영하면 점수가 누적된다(ZINCRBY)")
    @Test
    void incrBy_accumulatesScore() {
        // Arrange
        LocalDate today = LocalDate.now(SEOUL);

        // Act
        rankingRepository.incrBy(List.of(new RankingScoreDelta(today, 101L, 1.0)));
        rankingRepository.incrBy(List.of(new RankingScoreDelta(today, 101L, 0.5)));

        // Assert
        assertThat(redisTemplate.opsForZSet().score(keyOf(today), "101")).isCloseTo(1.5, within(1e-9));
    }
}
