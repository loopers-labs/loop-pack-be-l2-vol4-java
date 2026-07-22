package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingRepository;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RankingCarryOverRepositoryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long THREE_DAYS_SECONDS = 3 * 24 * 60 * 60L;

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

    @DisplayName("carry-over 는 오늘 점수 × weight 를 내일 판에 심고 내일 키에 만료를 건다")
    @Test
    void carryOver_seedsWeightedScores_withTtl() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate tomorrow = today.plusDays(1);
        redisTemplate.opsForZSet().add(keyOf(today), "101", 100.0);
        redisTemplate.opsForZSet().add(keyOf(today), "202", 50.0);

        rankingRepository.carryOver(today, tomorrow, 0.1);

        assertThat(redisTemplate.opsForZSet().score(keyOf(tomorrow), "101")).isCloseTo(10.0, within(1e-9));
        assertThat(redisTemplate.opsForZSet().score(keyOf(tomorrow), "202")).isCloseTo(5.0, within(1e-9));
        Long ttl = redisTemplate.getExpire(keyOf(tomorrow), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, THREE_DAYS_SECONDS);
    }

    @DisplayName("오늘 판이 비어 있으면 내일 판을 만들지 않는다")
    @Test
    void carryOver_emptySource_createsNothing() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate tomorrow = today.plusDays(1);

        rankingRepository.carryOver(today, tomorrow, 0.1);

        assertThat(redisTemplate.hasKey(keyOf(tomorrow))).isFalse();
    }
}
