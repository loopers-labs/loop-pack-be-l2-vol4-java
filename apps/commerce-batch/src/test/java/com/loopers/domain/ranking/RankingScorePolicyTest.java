package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScorePolicyTest {

    // commerce-streamer 의 실시간 랭킹과 동일한 가중치
    private final RankingScorePolicy policy = new RankingScorePolicy(0.1, 0.2, 0.7);

    @DisplayName("점수는 가중치 × 지표합의 선형결합이다.")
    @Test
    void combinesWeightedSums() {
        ProductRankScore score = policy.score(new ProductMetricsSum(1L, 10L, 3L, 100L));

        // 0.1*100 + 0.2*10 + 0.7*3 = 10 + 2 + 2.1
        assertThat(score.score()).isCloseTo(14.1, within(1e-9));
        assertThat(score.productId()).isEqualTo(1L);
        assertThat(score.likeCount()).isEqualTo(10L);
        assertThat(score.orderCount()).isEqualTo(3L);
        assertThat(score.viewCount()).isEqualTo(100L);
    }

    @DisplayName("주문 1건이 좋아요 3건보다 높게 평가된다(과제 기준).")
    @Test
    void orderOutweighsThreeLikes() {
        double oneOrder = policy.score(new ProductMetricsSum(1L, 0L, 1L, 0L)).score();
        double threeLikes = policy.score(new ProductMetricsSum(2L, 3L, 0L, 0L)).score();

        assertThat(oneOrder).isGreaterThan(threeLikes);
    }

    @DisplayName("좋아요 순증감이 음수면(취소 우세) 점수를 끌어내린다.")
    @Test
    void negativeLikeDeltaLowersScore() {
        double score = policy.score(new ProductMetricsSum(1L, -5L, 0L, 0L)).score();

        assertThat(score).isCloseTo(-1.0, within(1e-9));
    }
}
