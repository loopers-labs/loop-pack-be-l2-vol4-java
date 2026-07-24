package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankAggregateTest {

    @DisplayName("랭킹 점수는 0.1*조회 + 0.2*좋아요 + 0.6*판매 가중합이다")
    @Test
    void scoreIsWeightedSum() {
        // given
        RankAggregate aggregate = new RankAggregate(1L, 10L, 5L, 100L);

        // when
        double score = aggregate.score();

        // then - 0.1*100 + 0.2*10 + 0.6*5 = 10 + 2 + 3 = 15
        assertThat(score).isEqualTo(15.0);
    }

    @DisplayName("판매가 조회보다 가중치가 커, 판매 1건이 조회 1건보다 점수를 더 많이 올린다")
    @Test
    void salesOutweighsView() {
        // given
        RankAggregate oneSale = new RankAggregate(1L, 0L, 1L, 0L);
        RankAggregate oneView = new RankAggregate(2L, 0L, 0L, 1L);

        // when // then
        assertThat(oneSale.score()).isGreaterThan(oneView.score());
    }
}
