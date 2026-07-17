package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@DisplayName("RankingRedisRepository 통합 테스트 (Redis Testcontainers)")
class RankingRedisRepositoryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 16);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("increment")
    class Increment {

        @Test
        @DisplayName("같은 상품에 두 번 누적하면 점수가 합산되고, 키에 TTL 이 붙는다")
        void accumulatesAndSetsTtl() {
            rankingRepository.increment(RankingSignal.VIEW, TODAY, 1L, 1);
            rankingRepository.increment(RankingSignal.VIEW, TODAY, 1L, 1);

            String key = RankingKeys.raw(RankingSignal.VIEW, TODAY);
            assertThat(score(key, 1L)).isEqualTo(2.0);
            assertThat(redisTemplate.getExpire(key)).isPositive();
        }

        @Test
        @DisplayName("음수 델타로 0 아래로 내려갈 수 있다 — 클램프하지 않는다(취소 반영의 회계 흔적)")
        void allowsNegativeScore() {
            rankingRepository.increment(RankingSignal.LIKE, TODAY, 1L, -1);

            assertThat(score(RankingKeys.raw(RankingSignal.LIKE, TODAY), 1L)).isEqualTo(-1.0);
        }
    }

    @Nested
    @DisplayName("compose")
    class Compose {

        private final Map<RankingSignal, Double> weights = Map.of(
                RankingSignal.VIEW, 0.1,
                RankingSignal.LIKE, 0.2,
                RankingSignal.ORDER_COUNT, 0.7,
                RankingSignal.ORDER_QTY, 0.0
        );

        @Test
        @DisplayName("raw 보드들을 가중 합산해 display 보드를 만든다")
        void weightedSum() {
            rankingRepository.increment(RankingSignal.VIEW, TODAY, 1L, 10);        // 10 × 0.1 = 1.0
            rankingRepository.increment(RankingSignal.LIKE, TODAY, 1L, 5);         // 5 × 0.2 = 1.0
            rankingRepository.increment(RankingSignal.ORDER_COUNT, TODAY, 1L, 2);  // 2 × 0.7 = 1.4
            rankingRepository.increment(RankingSignal.ORDER_QTY, TODAY, 1L, 100);  // 100 × 0.0 = 0

            rankingRepository.compose(TODAY, weights);

            assertThat(score(RankingKeys.display(TODAY), 1L)).isCloseTo(3.4, within(1e-9));
            assertThat(redisTemplate.getExpire(RankingKeys.display(TODAY))).isPositive();
        }

        @Test
        @DisplayName("재합성해도 결과가 같다(덮어쓰기 멱등) — 다중 인스턴스 동시 실행 무해의 근거")
        void recomposeIsIdempotent() {
            rankingRepository.increment(RankingSignal.ORDER_COUNT, TODAY, 1L, 1);

            rankingRepository.compose(TODAY, weights);
            rankingRepository.compose(TODAY, weights);

            assertThat(score(RankingKeys.display(TODAY), 1L)).isCloseTo(0.7, within(1e-9));
        }

        @Test
        @DisplayName("가중치를 바꿔 재합성하면 이미 쌓인 당일 데이터 전체에 소급 반영된다")
        void weightChangeAppliesRetroactively() {
            rankingRepository.increment(RankingSignal.VIEW, TODAY, 1L, 10);
            rankingRepository.compose(TODAY, weights);
            assertThat(score(RankingKeys.display(TODAY), 1L)).isCloseTo(1.0, within(1e-9));

            Map<RankingSignal, Double> newWeights = Map.of(
                    RankingSignal.VIEW, 0.5,
                    RankingSignal.LIKE, 0.2,
                    RankingSignal.ORDER_COUNT, 0.7,
                    RankingSignal.ORDER_QTY, 0.0
            );
            rankingRepository.compose(TODAY, newWeights);

            assertThat(score(RankingKeys.display(TODAY), 1L)).isCloseTo(5.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("carryOver")
    class CarryOver {

        @Test
        @DisplayName("전일 점수 × 계수를 당일 raw 에 시드하되, 당일에 이미 쌓인 점수를 보존한다")
        void seedsWhilePreservingToday() {
            rankingRepository.increment(RankingSignal.VIEW, YESTERDAY, 1L, 100);
            rankingRepository.increment(RankingSignal.VIEW, TODAY, 1L, 3); // 자정~잡 실행 사이 먼저 쌓인 당일 증분

            boolean executed = rankingRepository.carryOver(YESTERDAY, TODAY, 0.1);

            assertThat(executed).isTrue();
            // 명세 원형(전일만 소스)이면 3 이 지워지고 10 — 오늘보존 변형이라 3 + 100×0.1 = 13
            assertThat(score(RankingKeys.raw(RankingSignal.VIEW, TODAY), 1L)).isCloseTo(13.0, within(1e-9));
        }

        @Test
        @DisplayName("재실행하면 마커 가드로 건너뛴다 — 시드 중복 가산 방지")
        void secondRunIsGuardedByMarker() {
            rankingRepository.increment(RankingSignal.VIEW, YESTERDAY, 1L, 100);

            assertThat(rankingRepository.carryOver(YESTERDAY, TODAY, 0.1)).isTrue();
            assertThat(rankingRepository.carryOver(YESTERDAY, TODAY, 0.1)).isFalse();

            assertThat(score(RankingKeys.raw(RankingSignal.VIEW, TODAY), 1L)).isCloseTo(10.0, within(1e-9));
        }

        @Test
        @DisplayName("전일 보드가 없으면 당일 점수 그대로 no-op 이다")
        void missingYesterdayIsNoOp() {
            rankingRepository.increment(RankingSignal.VIEW, TODAY, 1L, 3);

            boolean executed = rankingRepository.carryOver(YESTERDAY, TODAY, 0.1);

            assertThat(executed).isTrue();
            assertThat(score(RankingKeys.raw(RankingSignal.VIEW, TODAY), 1L)).isCloseTo(3.0, within(1e-9));
        }
    }

    private Double score(String key, long productId) {
        return redisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }
}
