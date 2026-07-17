package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("domain")
class RankingScorePolicyTest {

    private final RankingScorePolicy rankingScorePolicy = new RankingScorePolicy();

    @DisplayName("조회 점수는 weight(0.1) x score(1)이다.")
    @Test
    void viewScore_isWeightTimesBaseScore() {
        assertThat(rankingScorePolicy.viewScore()).isEqualTo(0.1);
    }

    @DisplayName("좋아요 점수는 weight(0.2) x score(1)이다.")
    @Test
    void likeScore_isWeightTimesBaseScore() {
        assertThat(rankingScorePolicy.likeScore()).isEqualTo(0.2);
    }

    @DisplayName("주문 점수는 weight(0.6) x price x quantity이다.")
    @Test
    void orderScore_isWeightTimesPriceTimesQuantity() {
        assertThat(rankingScorePolicy.orderScore(10_000L, 2L)).isEqualTo(0.6 * 10_000 * 2);
    }

    @DisplayName("가중치를 적용하면, 주문 1건의 점수가 좋아요 3건의 누적 점수보다 높다.")
    @Test
    void orderScore_outweighsThreeLikeScores() {
        double oneOrder = rankingScorePolicy.orderScore(1_000L, 1L);
        double threeLikes = rankingScorePolicy.likeScore() * 3;

        assertThat(oneOrder).isGreaterThan(threeLikes);
    }
}
