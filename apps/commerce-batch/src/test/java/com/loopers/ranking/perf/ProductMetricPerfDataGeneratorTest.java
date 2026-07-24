package com.loopers.ranking.perf;

import com.loopers.ranking.perf.ProductMetricPerfDataGenerator.DatasetDefinition;
import com.loopers.ranking.perf.ProductMetricPerfDataGenerator.ProductGroup;
import com.loopers.ranking.perf.ProductMetricPerfDataGenerator.ProductMetricSeedRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricPerfDataGeneratorTest {

    private static final DatasetDefinition SMALL_DATASET = new DatasetDefinition(
        "product-ranking-test",
        "ranking-perf-test-v1",
        1,
        18L,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 4),
        List.of(
            new ProductGroup(2, 4, 1_000, 2_000),
            new ProductGroup(2, 2, 500, 1_000),
            new ProductGroup(1, 1, 100, 500),
            new ProductGroup(1, 0, 0, 0)
        )
    );

    @DisplayName("기준 Dataset은 100만 상품 중 40만 상품의 Metric 226만 행을 정의한다.")
    @Test
    void defaultDatasetMatchesPerformanceScenario() {
        DatasetDefinition dataset = ProductMetricPerfDataGenerator
            .defaultGenerator()
            .dataset();

        assertThat(dataset.productCount()).isEqualTo(1_000_000);
        assertThat(dataset.activeProductCount()).isEqualTo(400_000);
        assertThat(dataset.metricCount()).isEqualTo(2_260_000);
        assertThat(dataset.metricStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(dataset.metricEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(dataset.randomSeed()).isEqualTo(42L);
        assertThat(dataset.productGroups()).containsExactly(
            new ProductGroup(10_000, 31, 10_000, 100_000),
            new ProductGroup(90_000, 15, 100, 10_000),
            new ProductGroup(300_000, 2, 10, 500),
            new ProductGroup(600_000, 0, 0, 0)
        );
    }

    @DisplayName("작은 Dataset도 상품군별 활동 일수만큼 중복 없는 Metric을 날짜와 상품 순서로 만든다.")
    @Test
    void generatesExpectedActivityDistributionInPrimaryKeyOrder() {
        List<ProductMetricSeedRow> rows = generateRows();

        assertThat(rows).hasSize(13);
        assertThat(rows)
            .doesNotHaveDuplicates()
            .isSortedAccordingTo(
                Comparator.comparing(ProductMetricSeedRow::metricDate)
                    .thenComparingLong(ProductMetricSeedRow::productId)
            );

        Map<Long, Long> activityDaysByProduct = rows.stream()
            .collect(Collectors.groupingBy(
                ProductMetricSeedRow::productId,
                Collectors.counting()
            ));
        assertThat(activityDaysByProduct)
            .containsEntry(1L, 4L)
            .containsEntry(2L, 4L)
            .containsEntry(3L, 2L)
            .containsEntry(4L, 2L)
            .containsEntry(5L, 1L)
            .doesNotContainKey(6L);
    }

    @DisplayName("같은 Generator Version과 Seed는 Metric 값까지 같은 Dataset을 재현한다.")
    @Test
    void sameSeedReproducesMetricValues() throws Exception {
        List<ProductMetricSeedRow> first = generateRows();
        List<ProductMetricSeedRow> second = generateRows();

        assertThat(second).isEqualTo(first);
        assertThat(fingerprint(first))
            .isEqualTo(
                "d79b91be7c0dc6266bb8965947433db84b7c6d40fa6fce8636b1b4d1a5c56d49"
            );
    }

    @DisplayName("생성 Metric은 조회·좋아요·주문 퍼널과 좋아요 취소 행을 포함한다.")
    @Test
    void generatedMetricsFollowFunnelAssumptions() {
        List<ProductMetricSeedRow> rows = generateRows();

        assertThat(rows).allSatisfy(metric -> {
            assertThat(metric.viewCount()).isPositive();
            assertThat(Math.abs(metric.likeDelta())).isLessThan(metric.viewCount());
            assertThat(metric.orderQuantity())
                .isNotNegative()
                .isLessThan(metric.viewCount());
            assertThat(metric.orderAmount()).isNotNegative();
            assertThat(metric.updatedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 5, 0, 0));
            if (metric.orderQuantity() == 0) {
                assertThat(metric.orderAmount()).isZero();
            } else {
                assertThat(metric.orderAmount() % metric.orderQuantity()).isZero();
            }
        });
        assertThat(rows).anySatisfy(metric ->
            assertThat(metric.likeDelta()).isNegative()
        );
    }

    private List<ProductMetricSeedRow> generateRows() {
        List<ProductMetricSeedRow> rows = new ArrayList<>();
        long generatedCount = new ProductMetricPerfDataGenerator(SMALL_DATASET)
            .generate(rows::add);

        assertThat(generatedCount).isEqualTo(13);
        return rows;
    }

    private String fingerprint(List<ProductMetricSeedRow> rows) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (ProductMetricSeedRow row : rows) {
            digest.update(row.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
