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

    private String todayKey() {
        return "ranking:all:" + LocalDate.now().format(DATE_FORMAT);
    }

    private Double scoreOf(Long productId) {
        return redisTemplate.opsForZSet().score(todayKey(), String.valueOf(productId));
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
}
