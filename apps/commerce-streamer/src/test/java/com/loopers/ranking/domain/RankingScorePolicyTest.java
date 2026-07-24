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

    @DisplayName("한 상품의 하루 점수는 시그널별 기여의 합이다 — batch 의 SUM(...)*가중치 집계와 같은 형태여야 한다")
    @Test
    void mixedSignals_sumToSameFormAsBatchAggregate() {
        // commerce-batch 의 집계 SQL 과 같은 조합·같은 기대값을 쓴다. 한쪽 가중치가 바뀌면 그 앱 테스트가 깨진다.
        // (완전한 크로스-앱 계약은 가중치 공유 위치가 정해져야 가능 — docs/week10 「점수 정책 공유」)
        double score = RankingScorePolicy.score(RankingSignal.VIEW, 10)
                + RankingScorePolicy.score(RankingSignal.LIKE, 5)
                + RankingScorePolicy.score(RankingSignal.ORDER, 2);

        // 10*0.1 + 5*0.2 + 2*0.6 = 1.0 + 1.0 + 1.2
        assertThat(score).isCloseTo(3.2, within(1e-9));
    }
}
