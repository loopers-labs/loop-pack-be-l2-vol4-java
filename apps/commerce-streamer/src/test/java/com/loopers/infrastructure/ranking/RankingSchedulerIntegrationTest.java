package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingScoreComposer;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.domain.ranking.RankingSlot;
import com.loopers.support.config.RankingProperties;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 스케줄러는 test 프로필에서 @ConditionalOnProperty 로 빈이 등록되지 않으므로(백그라운드 잡의 컨텍스트
 * 오염 차단 — R8 교훈) 직접 생성해 public 메서드를 호출한다. 검증 대상은 "언제 도는가"(Spring 몫)가
 * 아니라 "돌았을 때 옳은가".
 */
@SpringBootTest
@DisplayName("랭킹 스케줄러 통합 테스트 (compose·carry-over 직접 호출)")
class RankingSchedulerIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(RankingKeys.ZONE);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RankingProperties rankingProperties;

    @Autowired
    private RankingScoreComposer rankingScoreComposer;

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
    @DisplayName("RankingComposeScheduler")
    class ComposeScheduler {

        @Test
        @DisplayName("raw 신호를 기본 가중치(view 0.1/like 0.2/order 0.7)로 합성해 display 순위를 만든다 — 주문 1건 > 좋아요 3건")
        void composesDisplayBoardWithConfiguredWeights() {
            // 상품 1: 좋아요 3건 = 0.6 / 상품 2: 주문 1건 = 0.7 → 주문 상품이 상위
            seed(RankingSignal.LIKE, TODAY, 1L, 3);
            seed(RankingSignal.ORDER_COUNT, TODAY, 2L, 1);

            new RankingComposeScheduler(rankingScoreComposer).compose();

            Set<String> topFirst = redisTemplate.opsForZSet().reverseRange(RankingKeys.display(TODAY), 0, 0);
            assertThat(topFirst).containsExactly("2");
            assertThat(score(RankingKeys.display(TODAY), 2L)).isCloseTo(0.7, within(1e-9));
            assertThat(score(RankingKeys.display(TODAY), 1L)).isCloseTo(0.6, within(1e-9));
        }

        @Test
        @DisplayName("전일 raw 도 함께 재합성한다 — 자정 이후 도착한 전일 이벤트가 전일 display 에 반영")
        void recomposesYesterdayForLateEvents() {
            seed(RankingSignal.VIEW, YESTERDAY, 1L, 10);

            new RankingComposeScheduler(rankingScoreComposer).compose();

            assertThat(score(RankingKeys.display(YESTERDAY), 1L)).isCloseTo(1.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("RankingCarryOverScheduler")
    class CarryOverScheduler {

        @Test
        @DisplayName("당일 raw × 계수(0.1)를 익일 raw 에 미리 시드하고, 재실행은 마커로 skip 된다")
        void seedsOnceGuardedByMarker() {
            seed(RankingSignal.ORDER_COUNT, TODAY, 1L, 10);
            seed(RankingSignal.ORDER_COUNT, TOMORROW, 1L, 2); // 시드 전 익일 보드에 먼저 쌓인 증분 — 보존돼야 한다

            RankingCarryOverScheduler scheduler =
                    new RankingCarryOverScheduler(rankingRepository, rankingScoreComposer, rankingProperties);
            scheduler.carryOver();
            scheduler.carryOver(); // 재실행 — 시드 중복 가산되면 안 됨

            assertThat(score(RankingKeys.raw(RankingSignal.ORDER_COUNT, TOMORROW), 1L))
                    .isCloseTo(2.0 + 10 * rankingProperties.carryOver().weight(), within(1e-9));
        }

        @Test
        @DisplayName("시드 직후 익일 display 를 즉시 합성한다 — 자정에 키가 전환되는 순간 보드가 이미 존재한다")
        void composesImmediatelyAfterSeed() {
            seed(RankingSignal.ORDER_COUNT, TODAY, 1L, 10);

            new RankingCarryOverScheduler(rankingRepository, rankingScoreComposer, rankingProperties).carryOver();

            // display = 시드된 raw(10×0.1=1) × 주문 가중치(0.7)
            assertThat(score(RankingKeys.display(TOMORROW), 1L)).isCloseTo(0.7, within(1e-9));
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
