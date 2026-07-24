package com.loopers.batch.job.productrank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductRankScoreProcessor 단위 테스트")
class ProductRankScoreProcessorTest {

    private final ProductRankScoreProcessor processor = new ProductRankScoreProcessor();

    @DisplayName("[ECP] 조회/좋아요증감/구매수량에 가중치(0.1/0.2/0.7)를 적용해 score를 계산한다.")
    @Test
    void computesWeightedScore_whenRowHasPositiveMetrics() throws Exception {
        // arrange
        ProductMetricDailyRow row = new ProductMetricDailyRow(
                "PRD_01", LocalDate.of(2026, 7, 22), 10L, 5L, 2L
        );

        // act
        ProductRankScoreDelta delta = processor.process(row);

        // assert
        assertThat(delta.productId()).isEqualTo("PRD_01");
        assertThat(delta.scoreDelta()).isEqualTo(10 * 0.1 + 5 * 0.2 + 2 * 0.7);
        assertThat(delta.viewDelta()).isEqualTo(10L);
        assertThat(delta.likeDeltaDelta()).isEqualTo(5L);
        assertThat(delta.purchaseDelta()).isEqualTo(2L);
    }

    @DisplayName("[BVA] 좋아요 증감이 음수(순감소)인 row는 score에도 음의 기여를 한다.")
    @Test
    void appliesNegativeContribution_whenLikeDeltaIsNegative() throws Exception {
        // arrange
        ProductMetricDailyRow row = new ProductMetricDailyRow(
                "PRD_02", LocalDate.of(2026, 7, 22), 0L, -3L, 0L
        );

        // act
        ProductRankScoreDelta delta = processor.process(row);

        // assert
        assertThat(delta.scoreDelta()).isEqualTo(-3 * 0.2);
        assertThat(delta.likeDeltaDelta()).isEqualTo(-3L);
    }

    @DisplayName("[BVA] 모든 지표가 0인 row는 score도 0이다.")
    @Test
    void returnsZeroScore_whenAllMetricsAreZero() throws Exception {
        // arrange
        ProductMetricDailyRow row = new ProductMetricDailyRow(
                "PRD_03", LocalDate.of(2026, 7, 22), 0L, 0L, 0L
        );

        // act
        ProductRankScoreDelta delta = processor.process(row);

        // assert
        assertThat(delta.scoreDelta()).isZero();
    }
}
