package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RankingWeightsTest {

    @DisplayName("생성할 때,")
    @Nested
    class Create {

        @DisplayName("가중치 중 하나라도 음수이면 예외가 발생한다.")
        @Test
        void throwsException_whenAnyWeightIsNegative() {
            assertThatThrownBy(() -> new RankingWeights(-0.1, 0.2, 0.6))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @DisplayName("이벤트별 점수를 계산할 때,")
    @Nested
    class Score {

        @DisplayName("조회 점수는 가중치의 view 값이다.")
        @Test
        void viewScoreIsWeightsView() {
            assertThat(RankingWeights.DEFAULT.viewScore()).isCloseTo(0.1, within(1e-9));
        }

        @DisplayName("좋아요 점수는 가중치의 like 값, 좋아요 취소 점수는 그 반대 부호다.")
        @Test
        void likeAndUnlikeScoresAreOpposite() {
            assertThat(RankingWeights.DEFAULT.likeScore()).isCloseTo(0.2, within(1e-9));
            assertThat(RankingWeights.DEFAULT.unlikeScore()).isCloseTo(-0.2, within(1e-9));
        }

        @DisplayName("주문 점수는 가격*수량에 가중치의 order 값을 곱한 값이다.")
        @Test
        void orderScoreIsWeightedPriceTimesQuantity() {
            // given
            BigDecimal price = BigDecimal.valueOf(10_000);
            long quantity = 3;

            // when
            double score = RankingWeights.DEFAULT.orderScore(price, quantity);

            // then: 0.6 * 10000 * 3 = 18000
            assertThat(score).isCloseTo(18_000.0, within(1e-6));
        }

        @DisplayName("주문 1건의 점수가 좋아요 3건의 점수 합보다 크다.")
        @Test
        void orderScoreOutweighsMultipleLikes() {
            // given
            double orderScore = RankingWeights.DEFAULT.orderScore(BigDecimal.valueOf(10_000), 1);
            double threeLikesScore = RankingWeights.DEFAULT.likeScore() * 3;

            // then
            assertThat(orderScore).isGreaterThan(threeLikesScore);
        }

        @DisplayName("가중치를 다르게 주면 같은 이벤트라도 점수가 달라진다.")
        @Test
        void reflectsCustomWeights() {
            // given
            RankingWeights heavyOrder = new RankingWeights(0.1, 0.2, 1.0);

            // when
            double score = heavyOrder.orderScore(BigDecimal.valueOf(1_000), 1);

            // then: 1.0 * 1000 * 1 = 1000 (기본 가중치 0.6이면 600)
            assertThat(score).isCloseTo(1_000.0, within(1e-6));
        }
    }
}
