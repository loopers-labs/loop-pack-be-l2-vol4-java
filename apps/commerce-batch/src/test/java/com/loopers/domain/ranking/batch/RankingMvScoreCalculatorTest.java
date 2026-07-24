package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingMvScoreCalculatorTest {

    private final RankingMvScoreCalculator calculator = new RankingMvScoreCalculator();

    @DisplayName("calculate()를 실행할 때,")
    @Nested
    class Calculate {

        @DisplayName("view*0.1 + like*0.2 + order*0.6 가중합으로 점수를 계산한다.")
        @Test
        void calculatesWeightedSum() {
            RankingDailyMetricsAggregate aggregate = new RankingDailyMetricsAggregate(1L, 10L, 5L, 20L);

            RankingScoreCandidate candidate = calculator.calculate(aggregate);

            assertThat(candidate.productId()).isEqualTo(1L);
            assertThat(candidate.score()).isCloseTo(9.0, within(0.0001));
        }

        @DisplayName("모든 카운트가 0이면 점수는 0이다.")
        @Test
        void returnsZero_whenAllCountsAreZero() {
            RankingDailyMetricsAggregate aggregate = new RankingDailyMetricsAggregate(2L, 0L, 0L, 0L);

            RankingScoreCandidate candidate = calculator.calculate(aggregate);

            assertThat(candidate.score()).isCloseTo(0.0, within(0.0001));
        }
    }
}
