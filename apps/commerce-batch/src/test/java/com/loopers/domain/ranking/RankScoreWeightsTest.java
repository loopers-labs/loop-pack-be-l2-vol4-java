package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankScoreWeightsTest {

    @DisplayName("기본 가중치(1,3,10)로 view/like/order 건수에 점수를 부여한다.")
    @Test
    void computesScore_withDefaultWeights() {
        // given
        RankScoreWeights weights = RankScoreWeights.DEFAULT;

        // when
        long score = weights.score(2, 3, 4);

        // then
        // 2*1 + 3*3 + 4*10 = 51
        assertThat(score).isEqualTo(51L);
    }

    @DisplayName("모든 건수가 0이면 점수는 0이다.")
    @Test
    void returnsZero_whenAllCountsAreZero() {
        assertThat(RankScoreWeights.DEFAULT.score(0, 0, 0)).isZero();
    }

    @DisplayName("음수 가중치는 허용하지 않는다.")
    @Test
    void throwsException_whenWeightIsNegative() {
        assertAll(
            () -> assertThatThrownBy(() -> new RankScoreWeights(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class),
            () -> assertThatThrownBy(() -> new RankScoreWeights(0, -1, 0)).isInstanceOf(IllegalArgumentException.class),
            () -> assertThatThrownBy(() -> new RankScoreWeights(0, 0, -1)).isInstanceOf(IllegalArgumentException.class)
        );
    }
}
