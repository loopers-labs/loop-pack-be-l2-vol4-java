package com.loopers.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScorePolicyTest {

    // 기본 가중치(view 0.1 / like 0.2 / order 0.6)를 설정에서 주입받는 구조
    private final RankingScorePolicy policy =
        new RankingScorePolicy(new RankingWeightProperties(0.1, 0.2, 0.6));

    @DisplayName("집계 카운트로 점수를 계산할 때,")
    @Nested
    class Score {

        @DisplayName("view/like는 선형, sales는 log10(1+n)으로 가중합한 점수를 반환한다.")
        @Test
        void returnsWeightedSum_whenCountsAreProvided() {
            // arrange
            long viewCount = 10L;
            long likeCount = 5L;
            long salesCount = 3L;
            double expected = 10 * 0.1 + 5 * 0.2 + Math.log10(1 + 3.0) * 0.6;

            // act
            double actual = policy.score(viewCount, likeCount, salesCount);

            // assert
            assertThat(actual).isCloseTo(expected, within(1e-9));
        }

        @DisplayName("sales는 log10(1+n)으로 정규화돼 한 상품의 대량 판매가 전부를 부수지 않는다.")
        @Test
        void normalizesSalesWithLog_whenSalesCountIsLarge() {
            // arrange
            double tenSales = policy.score(0L, 0L, 10L);
            double hundredSales = policy.score(0L, 0L, 100L);

            // act & assert — 10배 팔려도 점수는 log 스케일(약 2배)로만 오른다
            assertThat(hundredSales).isLessThan(tenSales * 3);
            assertThat(hundredSales).isGreaterThan(tenSales);
        }

        @DisplayName("모든 카운트가 0이면 점수는 0.0이다.")
        @Test
        void returnsZero_whenAllCountsAreZero() {
            assertThat(policy.score(0L, 0L, 0L)).isEqualTo(0.0);
        }
    }

    @DisplayName("설정으로 주입한 가중치가,")
    @Nested
    class ConfiguredWeight {

        // 재배포 없이 가중치를 바꾸는 시나리오 — 설정값이 그대로 점수에 반영돼야 한다
        private final RankingScorePolicy custom =
            new RankingScorePolicy(new RankingWeightProperties(0.15, 0.25, 0.5));

        @DisplayName("가중합 점수에 그대로 반영된다.")
        @Test
        void reflectsConfiguredWeights() {
            // arrange
            double expected = 4 * 0.15 + 2 * 0.25 + Math.log10(1 + 9.0) * 0.5;

            // act & assert
            assertThat(custom.score(4L, 2L, 9L)).isCloseTo(expected, within(1e-9));
        }
    }
}
