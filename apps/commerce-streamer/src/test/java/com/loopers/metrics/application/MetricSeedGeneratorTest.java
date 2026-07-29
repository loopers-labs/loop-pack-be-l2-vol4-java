package com.loopers.metrics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드 카운트는 결정적이고 상품마다 편차가 있어야 한다 — 전 상품이 균등하면 상위권이 무작위라 순위 검증이 안 된다.
 * 상품별 기본 인기도에 일별 변동을 얹는다. 순수 로직(I/O 없음).
 */
class MetricSeedGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 20);

    @Test
    @DisplayName("같은 입력이면 같은 카운트를 낸다 — 재현 가능")
    void givenSameInput_whenGenerated_thenDeterministic() {
        assertThat(MetricSeedGenerator.generate(100L, DAY))
                .isEqualTo(MetricSeedGenerator.generate(100L, DAY));
    }

    @Test
    @DisplayName("상품이 다르면 카운트가 갈린다 — 순위에 편차가 생긴다")
    void givenDifferentProducts_whenGenerated_thenDiffer() {
        MetricSeedGenerator.DayCount a = MetricSeedGenerator.generate(100L, DAY);
        MetricSeedGenerator.DayCount b = MetricSeedGenerator.generate(200L, DAY);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("같은 상품도 날짜가 다르면 변동이 있다")
    void givenDifferentDays_whenGenerated_thenVary() {
        assertThat(MetricSeedGenerator.generate(100L, DAY))
                .isNotEqualTo(MetricSeedGenerator.generate(100L, DAY.plusDays(1)));
    }

    @Test
    @DisplayName("카운트는 음수가 아니다")
    void generated_countsAreNonNegative() {
        for (long id = 1; id <= 50; id++) {
            MetricSeedGenerator.DayCount c = MetricSeedGenerator.generate(id, DAY);
            assertThat(c.view()).isNotNegative();
            assertThat(c.like()).isNotNegative();
            assertThat(c.sales()).isNotNegative();
        }
    }

    @Test
    @DisplayName("조회 ≥ 좋아요 ≥ 판매 경향 — 흔한 신호일수록 많다")
    void generated_viewsDominateSales() {
        long viewTotal = 0;
        long salesTotal = 0;
        for (long id = 1; id <= 100; id++) {
            MetricSeedGenerator.DayCount c = MetricSeedGenerator.generate(id, DAY);
            viewTotal += c.view();
            salesTotal += c.sales();
        }
        assertThat(viewTotal).isGreaterThan(salesTotal);
    }
}
