package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.domain.ranking.RankingSlot;
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
    @DisplayName("incrementAll (배치 파이프라인)")
    class IncrementAll {

        @Test
        @DisplayName("여러 슬롯을 한 번에 누적하고(음수 허용), 손댄 모든 키에 TTL 이 붙는다")
        void writesAllSlotsWithTtl() {
            seed(RankingSignal.VIEW, TODAY, 1L, 1); // 기존 누적분 — 배치가 덮지 않고 더해야 한다

            rankingRepository.incrementAll(Map.of(
                    new RankingSlot(RankingSignal.VIEW, TODAY, 1L), 3.0,
                    new RankingSlot(RankingSignal.LIKE, TODAY, 1L), -1.0,
                    new RankingSlot(RankingSignal.ORDER_QTY, YESTERDAY, 2L), 5.0));

            assertThat(score(RankingKeys.raw(RankingSignal.VIEW, TODAY), 1L)).isEqualTo(4.0);
            assertThat(score(RankingKeys.raw(RankingSignal.LIKE, TODAY), 1L)).isEqualTo(-1.0);
            assertThat(score(RankingKeys.raw(RankingSignal.ORDER_QTY, YESTERDAY), 2L)).isEqualTo(5.0);
            assertThat(redisTemplate.getExpire(RankingKeys.raw(RankingSignal.VIEW, TODAY))).isPositive();
            assertThat(redisTemplate.getExpire(RankingKeys.raw(RankingSignal.ORDER_QTY, YESTERDAY))).isPositive();
        }

        @Test
        @DisplayName("빈 배치는 no-op 이다")
        void emptyBatchIsNoOp() {
            rankingRepository.incrementAll(Map.of());

            assertThat(redisTemplate.keys("ranking:*")).isEmpty();
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
            seed(RankingSignal.VIEW, TODAY, 1L, 10);        // 10 × 0.1 = 1.0
            seed(RankingSignal.LIKE, TODAY, 1L, 5);         // 5 × 0.2 = 1.0
            seed(RankingSignal.ORDER_COUNT, TODAY, 1L, 2);  // 2 × 0.7 = 1.4
            seed(RankingSignal.ORDER_QTY, TODAY, 1L, 100);  // 100 × 0.0 = 0

            rankingRepository.compose(TODAY, weights);

            assertThat(score(RankingKeys.display(TODAY), 1L)).isCloseTo(3.4, within(1e-9));
            assertThat(redisTemplate.getExpire(RankingKeys.display(TODAY))).isPositive();
        }

        @Test
        @DisplayName("기본 가중치에서 주문 1건이 좋아요 3건을 이긴다 (0.7 > 0.6) — 가중치가 순위에 반영되는 체크리스트 시나리오")
        void singleOrderBeatsThreeLikes() {
            seed(RankingSignal.LIKE, TODAY, 1L, 3);        // 3 × 0.2 = 0.6
            seed(RankingSignal.ORDER_COUNT, TODAY, 2L, 1); // 1 × 0.7 = 0.7

            rankingRepository.compose(TODAY, weights);

            assertThat(redisTemplate.opsForZSet().reverseRange(RankingKeys.display(TODAY), 0, -1))
                    .containsExactly("2", "1");
        }

        @Test
        @DisplayName("재합성해도 결과가 같다(덮어쓰기 멱등) — 다중 인스턴스 동시 실행 무해의 근거")
        void recomposeIsIdempotent() {
            seed(RankingSignal.ORDER_COUNT, TODAY, 1L, 1);

            rankingRepository.compose(TODAY, weights);
            rankingRepository.compose(TODAY, weights);

            assertThat(score(RankingKeys.display(TODAY), 1L)).isCloseTo(0.7, within(1e-9));
        }

        @Test
        @DisplayName("가중치를 바꿔 재합성하면 이미 쌓인 당일 데이터 전체에 소급 반영된다")
        void weightChangeAppliesRetroactively() {
            seed(RankingSignal.VIEW, TODAY, 1L, 10);
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
            seed(RankingSignal.VIEW, YESTERDAY, 1L, 100);
            seed(RankingSignal.VIEW, TODAY, 1L, 3); // 자정~잡 실행 사이 먼저 쌓인 당일 증분

            boolean executed = rankingRepository.carryOver(YESTERDAY, TODAY, 0.1);

            assertThat(executed).isTrue();
            // 명세 원형(전일만 소스)이면 3 이 지워지고 10 — 오늘보존 변형이라 3 + 100×0.1 = 13
            assertThat(score(RankingKeys.raw(RankingSignal.VIEW, TODAY), 1L)).isCloseTo(13.0, within(1e-9));
        }

        @Test
        @DisplayName("재실행하면 마커 가드로 건너뛴다 — 시드 중복 가산 방지")
        void secondRunIsGuardedByMarker() {
            seed(RankingSignal.VIEW, YESTERDAY, 1L, 100);

            assertThat(rankingRepository.carryOver(YESTERDAY, TODAY, 0.1)).isTrue();
            assertThat(rankingRepository.carryOver(YESTERDAY, TODAY, 0.1)).isFalse();

            assertThat(score(RankingKeys.raw(RankingSignal.VIEW, TODAY), 1L)).isCloseTo(10.0, within(1e-9));
        }

        @Test
        @DisplayName("전일 보드가 없으면 당일 점수 그대로 no-op 이다")
        void missingYesterdayIsNoOp() {
            seed(RankingSignal.VIEW, TODAY, 1L, 3);

            boolean executed = rankingRepository.carryOver(YESTERDAY, TODAY, 0.1);

            assertThat(executed).isTrue();
            assertThat(score(RankingKeys.raw(RankingSignal.VIEW, TODAY), 1L)).isCloseTo(3.0, within(1e-9));
        }
    }

    private Double score(String key, long productId) {
        return redisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }

    /** 픽스처 시딩용 단건 누적 — 포트는 배치(incrementAll)만 노출하므로 테스트에서 한 슬롯짜리 배치로 감싼다. */
    private void seed(RankingSignal signal, LocalDate date, long productId, double delta) {
        rankingRepository.incrementAll(Map.of(new RankingSlot(signal, date, productId), delta));
    }
}
