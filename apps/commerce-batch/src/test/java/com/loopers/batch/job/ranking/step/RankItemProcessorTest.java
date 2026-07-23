package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductMetricRow;
import com.loopers.ranking.domain.RankingScorePolicy;
import com.loopers.ranking.domain.RankingWeightProperties;
import com.loopers.ranking.domain.WeeklyProductRankModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankItemProcessorTest {

    // 점수를 미리 정한 순서대로 돌려주는 정책 — SQL 정렬로 흘러온 순서를 흉내 내 불변식 검증만 떼어 시험한다
    private RankItemProcessor processorReturning(double... scores) {
        Deque<Double> queue = new ArrayDeque<>();
        for (double s : scores) {
            queue.add(s);
        }
        RankingScorePolicy fixed = new RankingScorePolicy(new RankingWeightProperties(0.1, 0.2, 0.6)) {
            @Override
            public double score(long viewCount, long likeCount, long salesCount) {
                return queue.poll();
            }
        };
        return new RankItemProcessor(fixed, "weekly");
    }

    private ProductMetricRow row(long productId) {
        return new ProductMetricRow(productId, 0, 0, 0); // 카운트는 무의미 — 점수는 위 정책이 정한다
    }

    @DisplayName("점수 내림차순으로 흘러오면,")
    @Nested
    class WhenDescending {

        @DisplayName("흘러온 순서대로 rank 1..N을 부여한다.")
        @Test
        void assignsSequentialRanks() {
            // arrange
            RankItemProcessor processor = processorReturning(10.0, 5.0, 2.0);

            // act
            var first = processor.process(row(11L));
            var second = processor.process(row(22L));
            var third = processor.process(row(33L));

            // assert
            assertThat(first).isInstanceOf(WeeklyProductRankModel.class);
            assertThat(first.getRank()).isEqualTo(1);
            assertThat(second.getRank()).isEqualTo(2);
            assertThat(third.getRank()).isEqualTo(3);
            assertThat(first.getProductId()).isEqualTo(11L);
            assertThat(third.getProductId()).isEqualTo(33L);
        }
    }

    @DisplayName("점수가 앞 행보다 커지면(정렬식↔정책식 불일치),")
    @Nested
    class WhenScoreIncreases {

        @DisplayName("허용치를 넘는 역전이면 IllegalStateException으로 배치를 실패시킨다.")
        @Test
        void throwsException_whenScoreExceedsPreviousBeyondEpsilon() {
            // arrange — 두 번째 점수가 첫 번째보다 크다(5.0 → 10.0)
            RankItemProcessor processor = processorReturning(5.0, 10.0);
            processor.process(row(1L)); // prevScore = 5.0

            // act & assert
            assertThatThrownBy(() -> processor.process(row(2L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정렬식↔정책식 불일치");
        }

        @DisplayName("허용치(0.000001) 이내의 소수점 오차는 정상으로 통과한다.")
        @Test
        void passes_whenScoreIncreaseIsWithinEpsilon() {
            // arrange — 두 번째가 아주 근소하게(5e-7) 크지만 허용치 이내
            RankItemProcessor processor = processorReturning(5.0, 5.0 + 5e-7);
            processor.process(row(1L));

            // act
            var second = processor.process(row(2L));

            // assert — 헛경보 없이 rank 2 부여
            assertThat(second.getRank()).isEqualTo(2);
        }
    }
}
