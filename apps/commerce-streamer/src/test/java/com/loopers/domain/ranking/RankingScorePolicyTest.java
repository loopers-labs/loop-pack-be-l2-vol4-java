package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScorePolicyTest {

    @DisplayName("조회 점수는 0.1이다.")
    @Test
    void viewScoreIsPointOne() {
        assertThat(RankingScorePolicy.viewScore()).isCloseTo(0.1, within(1e-9));
    }

    @DisplayName("좋아요 점수는 0.2, 좋아요 취소 점수는 -0.2다.")
    @Test
    void likeAndUnlikeScoresAreOpposite() {
        assertThat(RankingScorePolicy.likeScore()).isCloseTo(0.2, within(1e-9));
        assertThat(RankingScorePolicy.unlikeScore()).isCloseTo(-0.2, within(1e-9));
    }

    @DisplayName("주문 점수는 가격*수량에 0.6 가중치를 곱한 값이다.")
    @Test
    void orderScoreIsWeightedPriceTimesQuantity() {
        // given
        BigDecimal price = BigDecimal.valueOf(10_000);
        long quantity = 3;

        // when
        double score = RankingScorePolicy.orderScore(price, quantity);

        // then: 0.6 * 10000 * 3 = 18000
        assertThat(score).isCloseTo(18_000.0, within(1e-6));
    }

    @DisplayName("주문 1건의 점수가 좋아요 3건의 점수 합보다 크다.")
    @Test
    void orderScoreOutweighsMultipleLikes() {
        // given
        double orderScore = RankingScorePolicy.orderScore(BigDecimal.valueOf(10_000), 1);
        double threeLikesScore = RankingScorePolicy.likeScore() * 3;

        // then
        assertThat(orderScore).isGreaterThan(threeLikesScore);
    }
}
