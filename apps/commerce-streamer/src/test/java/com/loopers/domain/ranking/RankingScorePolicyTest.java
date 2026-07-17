package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScorePolicyTest {

    @DisplayName("조회 1건의 score는 0.1이다.")
    @Test
    void viewScore_isPointOne() {
        assertThat(RankingScorePolicy.viewScore()).isEqualTo(0.1);
    }

    @DisplayName("좋아요 1건의 score는 0.2이다.")
    @Test
    void likeScore_isPointTwo() {
        assertThat(RankingScorePolicy.likeScore()).isEqualTo(0.2);
    }

    @DisplayName("주문 score는 0.7 * log10(price*quantity + 1)이다.")
    @Test
    void orderScore_appliesLogNormalization() {
        double score = RankingScorePolicy.orderScore(10_000L, 2L);

        assertThat(score).isCloseTo(0.7 * Math.log10(20_000 + 1), within(0.0001));
    }

    @DisplayName("가격이 0이어도 orderScore는 예외 없이 0을 반환한다.")
    @Test
    void orderScore_returnsZero_whenAmountIsZero() {
        double score = RankingScorePolicy.orderScore(0L, 1L);

        assertThat(score).isEqualTo(0.0);
    }
}
