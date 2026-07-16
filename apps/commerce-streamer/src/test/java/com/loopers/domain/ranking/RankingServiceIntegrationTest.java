package com.loopers.domain.ranking;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RankingServiceIntegrationTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private RankingService rankingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private String keyOf(LocalDate date) {
        return "ranking:all:" + date.format(DATE_FORMAT);
    }

    private String todayKey() {
        return keyOf(LocalDate.now());
    }

    private Double scoreOf(Long productId) {
        return redisTemplate.opsForZSet().score(todayKey(), String.valueOf(productId));
    }

    private Double scoreOf(LocalDate date, Long productId) {
        return redisTemplate.opsForZSet().score(keyOf(date), String.valueOf(productId));
    }

    private void seedWeights(double view, double like, double order) {
        redisTemplate.opsForHash().put("ranking:weights", "view", String.valueOf(view));
        redisTemplate.opsForHash().put("ranking:weights", "like", String.valueOf(like));
        redisTemplate.opsForHash().put("ranking:weights", "order", String.valueOf(order));
    }

    @DisplayName("배치 커맨드를 반영할 때,")
    @Nested
    class ApplyBatch {

        @DisplayName("조회 커맨드 하나는 0.1점으로 반영된다.")
        @Test
        void addsViewWeight_whenSingleViewCommand() {
            // given
            Long productId = 1L;
            List<RankingCommand.UpdateRanking> commands = List.of(RankingCommand.UpdateRanking.view(productId));

            // when
            rankingService.applyBatch(commands);

            // then
            assertThat(scoreOf(productId)).isCloseTo(0.1, within(1e-9));
        }

        @DisplayName("좋아요 커맨드 이후 좋아요 취소 커맨드가 오면 점수가 상쇄된다.")
        @Test
        void cancelsOutScore_whenLikeFollowedByUnlike() {
            // given
            Long productId = 2L;
            List<RankingCommand.UpdateRanking> commands = List.of(
                    RankingCommand.UpdateRanking.like(productId),
                    RankingCommand.UpdateRanking.unlike(productId)
            );

            // when
            rankingService.applyBatch(commands);

            // then
            assertThat(scoreOf(productId)).isCloseTo(0.0, within(1e-9));
        }

        @DisplayName("주문 커맨드는 가격과 수량에 가중치를 곱한 점수로 반영된다.")
        @Test
        void addsWeightedPriceScore_whenOrderCommand() {
            // given
            Long productId = 3L;
            List<RankingCommand.UpdateRanking> commands = List.of(
                    RankingCommand.UpdateRanking.order(productId, 2L, BigDecimal.valueOf(10_000))
            );

            // when
            rankingService.applyBatch(commands);

            // then: 0.6 * 10000 * 2 = 12000
            assertThat(scoreOf(productId)).isCloseTo(12_000.0, within(1e-6));
        }

        @DisplayName("같은 상품에 대한 여러 커맨드는 배치 내에서 합산되어 한 번에 반영된다.")
        @Test
        void aggregatesScores_whenMultipleCommandsForSameProduct() {
            // given
            Long productId = 4L;
            List<RankingCommand.UpdateRanking> commands = List.of(
                    RankingCommand.UpdateRanking.view(productId),
                    RankingCommand.UpdateRanking.view(productId),
                    RankingCommand.UpdateRanking.like(productId)
            );

            // when
            rankingService.applyBatch(commands);

            // then: 0.1 + 0.1 + 0.2 = 0.4
            assertThat(scoreOf(productId)).isCloseTo(0.4, within(1e-9));
        }

        @DisplayName("서로 다른 상품의 점수는 각자 독립적으로 반영된다.")
        @Test
        void keepsScoresIndependent_whenDifferentProducts() {
            // given
            Long productIdA = 5L;
            Long productIdB = 6L;
            List<RankingCommand.UpdateRanking> commands = List.of(
                    RankingCommand.UpdateRanking.view(productIdA),
                    RankingCommand.UpdateRanking.like(productIdB)
            );

            // when
            rankingService.applyBatch(commands);

            // then
            assertThat(scoreOf(productIdA)).isCloseTo(0.1, within(1e-9));
            assertThat(scoreOf(productIdB)).isCloseTo(0.2, within(1e-9));
        }
    }

    @DisplayName("Redis에 저장된 가중치가 있을 때,")
    @Nested
    class ApplyBatchWithCustomWeights {

        @DisplayName("재배포 없이 다음 배치부터 바뀐 가중치가 즉시 반영된다.")
        @Test
        void reflectsUpdatedWeights_withoutRedeploy() {
            // given
            Long productId = 7L;
            seedWeights(0.1, 0.2, 1.0);

            // when
            rankingService.applyBatch(List.of(RankingCommand.UpdateRanking.order(productId, 1L, BigDecimal.valueOf(1_000))));

            // then: 1.0 * 1000 * 1 = 1000 (기본 가중치 0.6이었다면 600)
            assertThat(scoreOf(productId)).isCloseTo(1_000.0, within(1e-6));
        }

        @DisplayName("가중치 값이 손상되어 있으면 기본값으로 폴백해 정상 반영된다.")
        @Test
        void fallsBackToDefault_whenWeightValueIsCorrupted() {
            // given
            Long productId = 8L;
            redisTemplate.opsForHash().put("ranking:weights", "view", "NOT_A_NUMBER");

            // when
            rankingService.applyBatch(List.of(RankingCommand.UpdateRanking.view(productId)));

            // then: 기본 view 가중치 0.1로 폴백
            assertThat(scoreOf(productId)).isCloseTo(0.1, within(1e-9));
        }
    }

    @DisplayName("Score Carry-Over를 수행할 때,")
    @Nested
    class CarryOverScores {

        @DisplayName("from 날짜의 점수에 ratio를 곱해 to 날짜 키에 반영한다.")
        @Test
        void copiesScaledScores_fromSourceDateToTargetDate() {
            // given
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            Long productId = 9L;
            rankingService.applyBatch(List.of(RankingCommand.UpdateRanking.order(productId, 1L, BigDecimal.valueOf(1_000))));
            // 오늘 점수: 0.6 * 1000 * 1 = 600

            // when
            rankingService.carryOverScores(today, tomorrow, 0.1);

            // then: 600 * 0.1 = 60
            assertThat(scoreOf(tomorrow, productId)).isCloseTo(60.0, within(1e-6));
        }

        @DisplayName("이미 to 날짜에 점수가 있으면 carry-over된 점수가 더해진다.")
        @Test
        void addsToExistingTargetScore() {
            // given
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            Long productId = 10L;
            rankingService.applyBatch(List.of(RankingCommand.UpdateRanking.view(productId)));
            // 오늘 점수: 0.1
            redisTemplate.opsForZSet().add(keyOf(tomorrow), String.valueOf(productId), 5.0);

            // when
            rankingService.carryOverScores(today, tomorrow, 0.5);

            // then: 5.0 + (0.1 * 0.5) = 5.05
            assertThat(scoreOf(tomorrow, productId)).isCloseTo(5.05, within(1e-9));
        }

        @DisplayName("from 날짜에 점수가 없으면 아무 것도 반영하지 않는다.")
        @Test
        void doesNothing_whenSourceDateHasNoScores() {
            // given
            LocalDate emptyDate = LocalDate.of(2000, 1, 1);
            LocalDate target = LocalDate.of(2000, 1, 2);

            // when
            rankingService.carryOverScores(emptyDate, target, 0.1);

            // then
            assertThat(redisTemplate.opsForZSet().zCard(keyOf(target))).isZero();
        }
    }
}
