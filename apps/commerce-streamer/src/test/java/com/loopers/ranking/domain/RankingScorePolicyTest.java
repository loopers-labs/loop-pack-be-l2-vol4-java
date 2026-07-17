package com.loopers.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScorePolicyTest {

    @DisplayName("조회 점수는 가중치 0.1 × delta 다")
    @Test
    void viewScore_isWeightTimesDelta() {
        assertThat(RankingScorePolicy.score(RankingSignal.VIEW, 1)).isCloseTo(0.1, within(1e-9));
    }

    @DisplayName("좋아요 점수는 가중치 0.2 × delta 이며, 취소(-1)는 음수로 되돌린다")
    @Test
    void likeScore_isWeightTimesDelta_includingCancel() {
        assertThat(RankingScorePolicy.score(RankingSignal.LIKE, 1)).isCloseTo(0.2, within(1e-9));
        assertThat(RankingScorePolicy.score(RankingSignal.LIKE, -1)).isCloseTo(-0.2, within(1e-9));
    }

    @DisplayName("주문 점수는 가중치 0.6 × 수량 이다")
    @Test
    void orderScore_isWeightTimesQuantity() {
        assertThat(RankingScorePolicy.score(RankingSignal.ORDER, 3)).isCloseTo(1.8, within(1e-9));
    }
}
