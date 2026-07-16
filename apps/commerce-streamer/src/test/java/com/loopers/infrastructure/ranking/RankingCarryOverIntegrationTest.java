package com.loopers.infrastructure.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class RankingCarryOverIntegrationTest {

    @Autowired
    private ProductRanking productRanking;

    @Autowired
    private RankingCarryOverScheduler scheduler;

    @Autowired
    private RankingProperties rankingProperties;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
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

    private void seed(LocalDate date, Long productId, double score) {
        redisTemplate.opsForZSet().add(keyOf(date), String.valueOf(productId), score);
    }

    private Double score(LocalDate date, Long productId) {
        return redisTemplate.opsForZSet().score(keyOf(date), String.valueOf(productId));
    }

    @DisplayName("전일 점수에 weight 를 곱해 다음 날 키로 복사한다 (100·50 × 0.1 → 10·5).")
    @Test
    void carriesOverConfiguredRatio() {
        // arrange
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        seed(today, 101L, 100.0);
        seed(today, 202L, 50.0);

        // act
        productRanking.carryOver(today, tomorrow, 0.1);

        // assert
        assertThat(score(tomorrow, 101L)).isCloseTo(10.0, within(1e-9));
        assertThat(score(tomorrow, 202L)).isCloseTo(5.0, within(1e-9));
    }

    @DisplayName("carry-over 된 다음 날 키에 TTL(2일 이내)이 설정된다.")
    @Test
    void setsTtlOnTargetKey() {
        // arrange
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        seed(today, 101L, 100.0);

        // act
        productRanking.carryOver(today, tomorrow, 0.1);

        // assert
        Long ttlSeconds = redisTemplate.getExpire(keyOf(tomorrow), TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
        assertThat(ttlSeconds).isLessThanOrEqualTo(172800L);
    }

    @DisplayName("스케줄러는 오늘 점수를 설정된 weight 로 내일 키에 복사한다.")
    @Test
    void schedulerCarriesTodayToTomorrow() {
        // arrange
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        seed(today, 101L, 100.0);
        double weight = rankingProperties.carryOver().weight();

        // act
        scheduler.carryOver();

        // assert
        assertThat(score(tomorrow, 101L)).isCloseTo(100.0 * weight, within(1e-9));
    }

    @DisplayName("carry-over 된 판(작은 점수)은 다음 날 실제 활동에 금세 추월된다 (롱테일 부활 방지).")
    @Test
    void carriedBoardIsQuicklyOvertaken() {
        // arrange — 전날 1위 101(→10), 2위 202(→5) 로 이월
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        seed(today, 101L, 100.0);
        seed(today, 202L, 50.0);
        productRanking.carryOver(today, tomorrow, 0.1);

        // act — 다음 날 202 에 실제 활동이 조금 쌓이면(+6) 이월된 101(10)을 추월(11)
        redisTemplate.opsForZSet().incrementScore(keyOf(tomorrow), "202", 6.0);

        // assert
        List<String> ranked = List.copyOf(redisTemplate.opsForZSet().reverseRange(keyOf(tomorrow), 0, -1));
        assertThat(ranked).containsExactly("202", "101");
    }
}