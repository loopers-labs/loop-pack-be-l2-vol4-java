package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankingTop100AccumulatorTest {

    @DisplayName("add()로 후보를 누적하고 drainRanked()로 꺼낼 때,")
    @Nested
    class DrainRanked {

        @DisplayName("100개 이하로 넣으면 전부 점수 내림차순으로 순위가 매겨진다.")
        @Test
        void ranksAllCandidates_whenUnderCapacity() {
            RankingTop100Accumulator accumulator = new RankingTop100Accumulator();
            accumulator.add(new RankingScoreCandidate(1L, 10.0));
            accumulator.add(new RankingScoreCandidate(2L, 30.0));
            accumulator.add(new RankingScoreCandidate(3L, 20.0));

            List<RankingStagingRankRow> ranked = accumulator.drainRanked();

            assertThat(ranked).extracting(RankingStagingRankRow::rank).containsExactly(1, 2, 3);
            assertThat(ranked).extracting(RankingStagingRankRow::productId).containsExactly(2L, 3L, 1L);
            assertThat(ranked).extracting(RankingStagingRankRow::score).containsExactly(30.0, 20.0, 10.0);
        }

        @DisplayName("용량(100)을 넘는 후보가 들어오면 점수가 낮은 쪽부터 밀려나 상위 100개만 남는다.")
        @Test
        void keepsOnlyTop100_whenOverCapacity() {
            RankingTop100Accumulator accumulator = new RankingTop100Accumulator();
            for (long productId = 1; productId <= 150; productId++) {
                accumulator.add(new RankingScoreCandidate(productId, (double) productId));
            }

            List<RankingStagingRankRow> ranked = accumulator.drainRanked();

            assertThat(ranked).hasSize(100);
            assertThat(ranked.get(0).productId()).isEqualTo(150L);
            assertThat(ranked.get(0).score()).isEqualTo(150.0);
            assertThat(ranked.get(99).productId()).isEqualTo(51L);
        }

        @DisplayName("용량이 찬 상태에서 최저 점수보다 낮은 후보는 버려진다.")
        @Test
        void discardsLowerScore_whenAtCapacity() {
            RankingTop100Accumulator accumulator = new RankingTop100Accumulator();
            for (long productId = 1; productId <= 100; productId++) {
                accumulator.add(new RankingScoreCandidate(productId, (double) productId));
            }
            accumulator.add(new RankingScoreCandidate(999L, 0.5));

            List<RankingStagingRankRow> ranked = accumulator.drainRanked();

            assertThat(ranked).hasSize(100);
            assertThat(ranked).extracting(RankingStagingRankRow::productId).doesNotContain(999L);
        }

        @DisplayName("점수가 같으면 productId 오름차순으로 순위를 매긴다.")
        @Test
        void tieBreaksByProductIdAscending_whenScoresAreEqual() {
            RankingTop100Accumulator accumulator = new RankingTop100Accumulator();
            accumulator.add(new RankingScoreCandidate(20L, 5.0));
            accumulator.add(new RankingScoreCandidate(10L, 5.0));

            List<RankingStagingRankRow> ranked = accumulator.drainRanked();

            assertThat(ranked).extracting(RankingStagingRankRow::productId).containsExactly(10L, 20L);
        }

        @DisplayName("아무것도 넣지 않으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNothingAdded() {
            RankingTop100Accumulator accumulator = new RankingTop100Accumulator();

            assertThat(accumulator.drainRanked()).isEmpty();
        }
    }
}
