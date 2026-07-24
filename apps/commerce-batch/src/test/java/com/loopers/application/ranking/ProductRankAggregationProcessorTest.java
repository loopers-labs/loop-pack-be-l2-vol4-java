package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRankAggregationProcessorTest {

    @Test
    @DisplayName("기간 내 일자별 랭킹 점수를 상품별로 합산하고 활성 상품만 순위로 만든다.")
    void aggregate_ShouldSumScoresByProductAndExcludeInactiveProducts() {
        ProductRankAggregationProcessor processor = new ProductRankAggregationProcessor(
            productIds -> Set.of(1L, 3L)
        );

        List<ProductRankSnapshot> snapshots = processor.aggregate(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            10L,
            List.of(
                new ProductMetricInput(LocalDate.of(2026, 7, 20), 1L, 10.0),
                new ProductMetricInput(LocalDate.of(2026, 7, 21), 1L, 5.0),
                new ProductMetricInput(LocalDate.of(2026, 7, 20), 2L, 100.0),
                new ProductMetricInput(LocalDate.of(2026, 7, 20), 3L, 7.0)
            )
        );

        assertThat(snapshots).extracting(ProductRankSnapshot::getProductId)
            .containsExactly(1L, 3L);
        assertThat(snapshots).extracting(ProductRankSnapshot::getRankNo)
            .containsExactly(1, 2);
        assertThat(snapshots).extracting(ProductRankSnapshot::getScore)
            .containsExactly(15.0, 7.0);
        assertThat(snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.getBatchRunId()).isEqualTo(10L);
            assertThat(snapshot.isActive()).isFalse();
        });
    }

    @Test
    @DisplayName("랭킹 Snapshot은 점수 내림차순으로 최대 100개만 생성한다.")
    void aggregate_ShouldKeepTop100ByScore() {
        ProductRankAggregationProcessor processor = new ProductRankAggregationProcessor(
            productIds -> Set.copyOf(productIds)
        );
        List<ProductMetricInput> metrics = java.util.stream.LongStream.rangeClosed(1, 101)
            .mapToObj(productId -> new ProductMetricInput(
                LocalDate.of(2026, 7, 20),
                productId,
                (double) productId
            ))
            .toList();

        List<ProductRankSnapshot> snapshots = processor.aggregate(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            11L,
            metrics
        );

        assertThat(snapshots).hasSize(100);
        assertThat(snapshots.get(0).getProductId()).isEqualTo(101L);
        assertThat(snapshots.get(0).getRankNo()).isEqualTo(1);
        assertThat(snapshots.get(99).getProductId()).isEqualTo(2L);
        assertThat(snapshots.get(99).getRankNo()).isEqualTo(100);
    }
}
