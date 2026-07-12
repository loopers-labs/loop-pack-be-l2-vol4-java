package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingScorePolicyTest {

    @DisplayName("조회 점수를 계산할 때, ")
    @Nested
    class ViewScore {
        @DisplayName("가중치 0.1을 반환한다.")
        @Test
        void returnsViewWeight() {
            assertThat(RankingScorePolicy.viewScore()).isEqualTo(0.1);
        }
    }

    @DisplayName("좋아요 점수를 계산할 때, ")
    @Nested
    class LikeScore {
        @DisplayName("가중치 0.2를 반환한다.")
        @Test
        void returnsLikeWeight() {
            assertThat(RankingScorePolicy.likeScore()).isEqualTo(0.2);
        }
    }

    @DisplayName("좋아요 취소 점수를 계산할 때, ")
    @Nested
    class UnlikeScore {
        @DisplayName("좋아요 가중치의 음수(-0.2)를 반환한다.")
        @Test
        void returnsNegativeLikeWeight() {
            assertThat(RankingScorePolicy.unlikeScore()).isEqualTo(-0.2);
        }
    }

    @DisplayName("주문 점수를 계산할 때, ")
    @Nested
    class OrderScore {
        @DisplayName("가중치(0.7)와 log10(price*quantity + 1)을 곱한 값을 반환한다.")
        @Test
        void returnsWeightedLogNormalizedScore() {
            // arrange: 999 * 1 + 1 = 1000 → log10(1000) = 3 (깔끔하게 떨어지는 값으로 검증)
            double score = RankingScorePolicy.orderScore(999, 1);

            // assert
            assertThat(score).isEqualTo(0.7 * 3);
        }

        @DisplayName("가격이 0이어도, 무한대가 아닌 유한한 값을 반환한다.")
        @Test
        void returnsFiniteScore_whenPriceIsZero() {
            // arrange
            double score = RankingScorePolicy.orderScore(0, 1);

            // assert
            assertThat(score).isZero();
            assertThat(Double.isFinite(score)).isTrue();
        }

        @DisplayName("가격이 비싸질수록 점수는 커지지만, 가격 배율만큼 극단적으로 커지지는 않는다.")
        @Test
        void compressesScoreGap_forExpensiveItems() {
            // arrange: 가격이 10배 차이나는 두 주문
            double cheapScore = RankingScorePolicy.orderScore(500_000, 1);
            double expensiveScore = RankingScorePolicy.orderScore(5_000_000, 1);

            // assert
            assertThat(expensiveScore).isGreaterThan(cheapScore);
            assertThat(expensiveScore / cheapScore).isLessThan(10.0); // 로그 정규화로 배율이 완만해짐
        }
    }
}
