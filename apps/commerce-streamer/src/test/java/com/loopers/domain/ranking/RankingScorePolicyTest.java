package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScorePolicyTest {

    private final RankingScorePolicy policy = new RankingScorePolicy();

    @DisplayName("조회 신호는 고정 가중치 0.1 을 점수 델타로 반환한다.")
    @Test
    void viewSignalScoresFixedWeight() {
        // given
        RankingSignal signal = RankingSignal.VIEW;

        // when
        double delta = policy.scoreFor(signal, 0);

        // then
        assertThat(delta).isEqualTo(0.1);
    }

    @DisplayName("좋아요 신호는 양의 가중치 0.2 를 점수 델타로 반환한다.")
    @Test
    void likeSignalScoresPositiveWeight() {
        // given
        RankingSignal signal = RankingSignal.LIKE;

        // when
        double delta = policy.scoreFor(signal, 0);

        // then
        assertThat(delta).isEqualTo(0.2);
    }

    @DisplayName("좋아요 취소 신호는 음의 가중치 -0.2 를 점수 델타로 반환한다.")
    @Test
    void unlikeSignalScoresNegativeWeight() {
        // given
        RankingSignal signal = RankingSignal.UNLIKE;

        // when
        double delta = policy.scoreFor(signal, 0);

        // then
        assertThat(delta).isEqualTo(-0.2);
    }

    @DisplayName("주문 신호는 가중치 0.7 에 금액을 log10(1+금액) 으로 정규화해 반영한다.")
    @Test
    void orderSignalScoresWeightTimesLogNormalizedAmount() {
        // given
        long lineAmount = 10_000L;

        // when
        double delta = policy.scoreFor(RankingSignal.ORDER, lineAmount);

        // then
        assertThat(delta).isCloseTo(0.7 * Math.log10(1 + lineAmount), within(1e-9));
    }

    @DisplayName("주문 1건의 점수가 좋아요 3건의 합산 점수보다 크다.")
    @Test
    void oneOrderOutweighsThreeLikes() {
        // given
        double oneOrder = policy.scoreFor(RankingSignal.ORDER, 10_000L);

        // when
        double threeLikes = 3 * policy.scoreFor(RankingSignal.LIKE, 0);

        // then
        assertThat(oneOrder).isGreaterThan(threeLikes);
    }

    @DisplayName("주문 금액이 0 이어도 점수는 음수나 NaN 이 아니다.")
    @Test
    void zeroAmountOrderYieldsNonNegativeFiniteScore() {
        // given
        long lineAmount = 0L;

        // when
        double delta = policy.scoreFor(RankingSignal.ORDER, lineAmount);

        // then
        assertThat(Double.isNaN(delta)).isFalse();
        assertThat(delta).isGreaterThanOrEqualTo(0.0);
    }
}
