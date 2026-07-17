package com.loopers.infrastructure.ranking;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
class RankingRedisRepositoryTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private RankingRedisRepository rankingRedisRepository;

    @Autowired
    @Qualifier("masterRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private String rankingKey(ZonedDateTime occurredAt) {
        return "ranking:all:" + occurredAt.withZoneSameInstant(ZONE).format(KEY_DATE_FORMAT);
    }

    @DisplayName("같은 상품에 점수를 여러 번 더하면, ZSET에 누적된다.")
    @Test
    void addScore_accumulatesOnSameProduct() {
        // arrange
        ZonedDateTime occurredAt = ZonedDateTime.now(ZONE);
        Long productId = 1L;

        // act
        rankingRedisRepository.addScore(productId, 0.1, occurredAt);
        rankingRedisRepository.addScore(productId, 0.1, occurredAt);

        // assert
        Double score = redisTemplate.opsForZSet().score(rankingKey(occurredAt), String.valueOf(productId));
        assertThat(score).isEqualTo(0.2);
    }

    @DisplayName("점수를 더하면, 키에 TTL이 설정된다.")
    @Test
    void addScore_setsExpiration() {
        // arrange
        ZonedDateTime occurredAt = ZonedDateTime.now(ZONE);

        // act
        rankingRedisRepository.addScore(1L, 1.0, occurredAt);

        // assert
        Long ttl = redisTemplate.getExpire(rankingKey(occurredAt));
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofDays(2).getSeconds());
    }

    @DisplayName("가중치 적용 결과, 주문 1건의 점수가 좋아요 3건의 누적 점수보다 높은 순위를 가진다.")
    @Test
    void orderScore_ranksHigherThanThreeLikeScores() {
        // arrange
        ZonedDateTime occurredAt = ZonedDateTime.now(ZONE);
        Long orderedProduct = 100L;
        Long likedProduct = 200L;

        // act: 주문 1건(0.6 x 1000 x 1 = 600) vs 좋아요 3건(0.2 x 3 = 0.6)
        rankingRedisRepository.addScore(orderedProduct, 600.0, occurredAt);
        rankingRedisRepository.addScore(likedProduct, 0.2, occurredAt);
        rankingRedisRepository.addScore(likedProduct, 0.2, occurredAt);
        rankingRedisRepository.addScore(likedProduct, 0.2, occurredAt);

        // assert
        String key = rankingKey(occurredAt);
        Long orderedRank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(orderedProduct));
        Long likedRank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(likedProduct));

        assertThat(orderedRank).isLessThan(likedRank);
    }

    @DisplayName("이월 시, 원본 랭킹의 상위 N개만 감쇠된 점수로 대상 날짜에 반영된다.")
    @Test
    void carryOverTopScores_appliesDecayedScoreToTopNOnly() {
        // arrange
        LocalDate today = LocalDate.now(ZONE);
        LocalDate tomorrow = today.plusDays(1);
        Long topProduct = 1L;
        Long secondProduct = 2L;
        Long excludedProduct = 3L;

        rankingRedisRepository.addScore(topProduct, 1000.0, ZonedDateTime.now(ZONE));
        rankingRedisRepository.addScore(secondProduct, 500.0, ZonedDateTime.now(ZONE));
        rankingRedisRepository.addScore(excludedProduct, 100.0, ZonedDateTime.now(ZONE));

        // act: 상위 2개만, 10%로 감쇠하여 이월
        rankingRedisRepository.carryOverTopScores(today, tomorrow, 2, 0.1);

        // assert
        String tomorrowKey = "ranking:all:" + tomorrow.format(KEY_DATE_FORMAT);
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, String.valueOf(topProduct))).isEqualTo(100.0);
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, String.valueOf(secondProduct))).isEqualTo(50.0);
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, String.valueOf(excludedProduct))).isNull();
    }

    @DisplayName("이월 대상 날짜에 원본 랭킹이 없으면, 아무 것도 반영되지 않는다.")
    @Test
    void carryOverTopScores_doesNothing_whenSourceRankingIsEmpty() {
        // arrange
        LocalDate today = LocalDate.now(ZONE);
        LocalDate tomorrow = today.plusDays(1);

        // act
        rankingRedisRepository.carryOverTopScores(today, tomorrow, 100, 0.1);

        // assert
        String tomorrowKey = "ranking:all:" + tomorrow.format(KEY_DATE_FORMAT);
        assertThat(redisTemplate.hasKey(tomorrowKey)).isFalse();
    }
}
