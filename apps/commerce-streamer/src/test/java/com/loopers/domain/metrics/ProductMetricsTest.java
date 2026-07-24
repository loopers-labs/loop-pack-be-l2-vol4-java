package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ProductMetricsTest {

    @Test
    @DisplayName("상품 메트릭은 이벤트 발생일과 상품 단위로 생성된다.")
    void create_ShouldCreateDailyProductMetrics() {
        ProductMetrics productMetrics = ProductMetrics.create(
            LocalDate.of(2026, 7, 14),
            1L
        );

        assertThat(productMetrics.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(productMetrics.getProductId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("조회와 좋아요 메트릭은 일자별 카운트와 랭킹 점수를 누적한다.")
    void addViewAndLike_ShouldIncreaseDailyCountAndRankingScore() {
        ProductMetrics productMetrics = ProductMetrics.create(
            LocalDate.of(2026, 7, 14),
            1L
        );

        productMetrics.addView(0.1);
        productMetrics.addLike(0.2);

        assertThat(productMetrics.getViewCount()).isEqualTo(1L);
        assertThat(productMetrics.getLikeCount()).isEqualTo(1L);
        assertThat(productMetrics.getDailyRankingScore()).isCloseTo(0.3, within(0.000_001));
    }

    @Test
    @DisplayName("주문 메트릭은 판매량, 주문 금액, 랭킹 점수를 함께 누적한다.")
    void addSales_ShouldIncreaseSalesCountOrderAmountAndRankingScore() {
        ProductMetrics productMetrics = ProductMetrics.create(
            LocalDate.of(2026, 7, 14),
            1L
        );

        productMetrics.addSales(2, BigDecimal.valueOf(10_000), 0.6 * Math.log(20_001));

        assertThat(productMetrics.getSalesCount()).isEqualTo(2L);
        assertThat(productMetrics.getOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(productMetrics.getDailyRankingScore()).isCloseTo(0.6 * Math.log(20_001), within(0.000_001));
    }
}
