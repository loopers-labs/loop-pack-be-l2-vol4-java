package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RankingRedisRepositoryIntegrationTest {

    @Autowired
    private RankingRedisRepository rankingRedisRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);
    private static final String KEY = "ranking:all:20260715";
    private static final Long PRODUCT_ID = 10L;
    private static final double OFFSET = 1e-6;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("incrementScore()를 호출할 때,")
    @Nested
    class IncrementScore {

        @DisplayName("같은 상품에 여러 번 가산하면 점수가 누적된다.")
        @Test
        void accumulatesScore_forSameProduct() {
            // arrange & act — 조회(0.1) + 좋아요(0.2) + 주문(6000)
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.1);
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.2);
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 6_000.0);

            // assert
            Double score = redisTemplate.opsForZSet().score(KEY, String.valueOf(PRODUCT_ID));
            assertThat(score).isCloseTo(6_000.3, within(OFFSET));
        }

        @DisplayName("음수 delta로 가산하면 점수가 감소한다 (좋아요 취소 감점).")
        @Test
        void decreasesScore_withNegativeDelta() {
            // arrange
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.2);

            // act
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, -0.2);

            // assert
            Double score = redisTemplate.opsForZSet().score(KEY, String.valueOf(PRODUCT_ID));
            assertThat(score).isCloseTo(0.0, within(OFFSET));
        }

        @DisplayName("점수가 높은 상품이 랭킹 상위(ZREVRANGE 앞)에 온다.")
        @Test
        void ranksHigherScoredProductFirst() {
            // arrange & act
            rankingRedisRepository.incrementScore(DATE, 1L, 0.1);
            rankingRedisRepository.incrementScore(DATE, 2L, 6_000.0);
            rankingRedisRepository.incrementScore(DATE, 3L, 0.6);

            // assert
            Set<String> top = redisTemplate.opsForZSet().reverseRange(KEY, 0, -1);
            assertThat(top).containsExactly("2", "3", "1");
        }

        @DisplayName("날짜가 다르면 서로 다른 일간 키에 적재된다.")
        @Test
        void separatesKeys_byDate() {
            // arrange & act
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.1);
            rankingRedisRepository.incrementScore(DATE.plusDays(1), PRODUCT_ID, 0.2);

            // assert
            assertThat(redisTemplate.opsForZSet().score(KEY, String.valueOf(PRODUCT_ID)))
                .isCloseTo(0.1, within(OFFSET));
            assertThat(redisTemplate.opsForZSet().score("ranking:all:20260716", String.valueOf(PRODUCT_ID)))
                .isCloseTo(0.2, within(OFFSET));
        }
    }

    @DisplayName("TTL 관리 —")
    @Nested
    class Ttl {

        @DisplayName("키 생성 시 TTL 2일이 설정된다.")
        @Test
        void setsTwoDayTtl_onKeyCreation() {
            // act
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.1);

            // assert — 2일(172800초)에서 오차 허용
            Long expireSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
            assertThat(expireSeconds).isBetween(172_800L - 60, 172_800L);
        }

        @DisplayName("추가 가산 시 TTL이 연장되지 않는다 — 만료가 계속 밀리는 것을 방지한다.")
        @Test
        void doesNotExtendTtl_onSubsequentIncrements() {
            // arrange
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.1);
            Long firstExpire = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);

            // act
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.2);

            // assert — TTL이 처음 설정값을 넘어 다시 늘어나지 않는다
            Long secondExpire = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
            assertThat(secondExpire).isLessThanOrEqualTo(firstExpire);
        }
    }

    @DisplayName("Carry-Over 지원 연산 —")
    @Nested
    class CarryOver {

        @DisplayName("findTopEntries()는 양수 점수 항목만 점수 내림차순으로 limit개 반환한다 (0점·음수 제외).")
        @Test
        void findsTopPositiveEntries_inDescendingOrder() {
            // arrange
            redisTemplate.opsForZSet().add(KEY, "1", 6_000.0);
            redisTemplate.opsForZSet().add(KEY, "2", 0.6);
            redisTemplate.opsForZSet().add(KEY, "3", 0.1);
            redisTemplate.opsForZSet().add(KEY, "4", 0.0);
            redisTemplate.opsForZSet().add(KEY, "5", -0.2);

            // act
            List<RankingEntry> result = rankingRedisRepository.findTopEntries(DATE, 2);

            // assert
            assertThat(result).containsExactly(
                new RankingEntry(1L, 6_000.0),
                new RankingEntry(2L, 0.6)
            );
        }

        @DisplayName("saveScoreIfAbsent()는 이미 점수가 있는 상품을 덮어쓰지 않는다 — 스케줄러 중복 실행에 안전.")
        @Test
        void doesNotOverwriteExistingScore() {
            // arrange
            rankingRedisRepository.saveScoreIfAbsent(DATE, PRODUCT_ID, 3_000.0);

            // act — 중복 실행 재현
            rankingRedisRepository.saveScoreIfAbsent(DATE, PRODUCT_ID, 999.0);

            // assert
            assertThat(redisTemplate.opsForZSet().score(KEY, String.valueOf(PRODUCT_ID)))
                .isCloseTo(3_000.0, within(1e-6));
        }

        @DisplayName("saveScoreIfAbsent()로 키가 생성되면 TTL 2일이 설정된다.")
        @Test
        void setsTtl_whenKeyIsCreatedByCarryOver() {
            // act
            rankingRedisRepository.saveScoreIfAbsent(DATE, PRODUCT_ID, 3_000.0);

            // assert
            Long expireSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
            assertThat(expireSeconds).isBetween(172_800L - 60, 172_800L);
        }

        @DisplayName("이월된 점수 위에 자정 이후 실제 이벤트 점수가 누적된다.")
        @Test
        void accumulatesRealEventsOnTopOfCarriedScore() {
            // arrange — 이월 점수 기록
            rankingRedisRepository.saveScoreIfAbsent(DATE, PRODUCT_ID, 3_000.0);

            // act — 자정 이후 조회 이벤트 재현
            rankingRedisRepository.incrementScore(DATE, PRODUCT_ID, 0.1);

            // assert
            assertThat(redisTemplate.opsForZSet().score(KEY, String.valueOf(PRODUCT_ID)))
                .isCloseTo(3_000.1, within(1e-6));
        }
    }
}
