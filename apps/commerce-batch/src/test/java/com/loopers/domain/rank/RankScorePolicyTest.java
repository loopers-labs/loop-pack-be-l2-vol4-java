package com.loopers.domain.rank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankScorePolicyTest {

    @DisplayName("카운트 가중합으로 점수를 계산한다 (조회 0.1 / 좋아요 0.2 / 판매 0.6).")
    @Test
    void computesWeightedScore() {
        // arrange
        long views = 10, likes = 5, sales = 3;

        // act
        double score = RankScorePolicy.score(views, likes, sales);

        // assert — 0.1*10 + 0.2*5 + 0.6*3 = 1.0 + 1.0 + 1.8 = 3.8
        assertThat(score).isCloseTo(3.8, within(1e-9));
    }

    @DisplayName("모든 카운트가 0이면 점수는 0이다 (경계값).")
    @Test
    void returnsZero_whenAllCountsZero() {
        assertThat(RankScorePolicy.score(0, 0, 0)).isEqualTo(0.0);
    }

    @DisplayName("판매가 조회·좋아요보다 순위에 더 크게 기여한다 (가중치 우선순위).")
    @Test
    void salesOutweighsViewsAndLikes() {
        // arrange — 판매 1건 vs 조회 5건
        double oneSale = RankScorePolicy.score(0, 0, 1);   // 0.6
        double fiveViews = RankScorePolicy.score(5, 0, 0);  // 0.5

        // assert
        assertThat(oneSale).isGreaterThan(fiveViews);
    }

    @DisplayName("ORDER BY용 SQL 표현식이 score()와 동일한 계수를 사용한다 (정렬-저장 일치).")
    @Test
    void scoreSqlUsesSameWeights() {
        // act
        String sql = RankScorePolicy.scoreSql("v", "l", "s");

        // assert
        assertThat(sql).isEqualTo("0.1 * v + 0.2 * l + 0.6 * s");
    }
}
