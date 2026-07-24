package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.ProductRankingEvent;
import com.loopers.domain.ranking.RankingEventType;
import com.loopers.domain.ranking.RankingScorePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsUpdateServiceTest {

    @Test
    @DisplayName("조회 이벤트를 수신하면 상품 지표의 조회수를 1 증가시킨다.")
    void update_WhenViewEvent_ShouldIncreaseViewCount() {
        // given
        FakeProductMetricsRepository productMetricsRepository = new FakeProductMetricsRepository();
        MetricsUpdateService metricsUpdateService = new MetricsUpdateService(
            productMetricsRepository,
            new RankingScorePolicy()
        );
        ProductRankingEvent event = new ProductRankingEvent(
            "event-1",
            RankingEventType.VIEW,
            1L,
            BigDecimal.ZERO,
            0,
            LocalDateTime.of(2026, 7, 14, 10, 0)
        );

        // when
        metricsUpdateService.update(event);

        // then
        ProductMetrics metrics = productMetricsRepository.saved;
        assertThat(metrics.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(metrics.getProductId()).isEqualTo(1L);
        assertThat(metrics.getViewCount()).isEqualTo(1L);
        assertThat(metrics.getLikeCount()).isZero();
        assertThat(metrics.getSalesCount()).isZero();
        assertThat(metrics.getDailyRankingScore()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("주문 이벤트를 수신하면 이벤트 발생일의 판매량, 주문 금액, 랭킹 점수를 누적한다.")
    void update_WhenOrderEvent_ShouldIncreaseSalesMetrics() {
        // given
        FakeProductMetricsRepository productMetricsRepository = new FakeProductMetricsRepository(
            ProductMetrics.create(LocalDate.of(2026, 7, 14), 1L)
        );
        RankingScorePolicy rankingScorePolicy = new RankingScorePolicy();
        MetricsUpdateService metricsUpdateService = new MetricsUpdateService(
            productMetricsRepository,
            rankingScorePolicy
        );
        ProductRankingEvent event = new ProductRankingEvent(
            "event-2",
            RankingEventType.ORDER,
            1L,
            BigDecimal.valueOf(10_000),
            2,
            LocalDateTime.of(2026, 7, 14, 10, 0)
        );

        // when
        metricsUpdateService.update(event);

        // then
        ProductMetrics metrics = productMetricsRepository.saved;
        assertThat(metrics.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(metrics.getSalesCount()).isEqualTo(2L);
        assertThat(metrics.getOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(metrics.getDailyRankingScore()).isEqualTo(
            rankingScorePolicy.calculateScore(RankingEventType.ORDER, BigDecimal.valueOf(10_000), 2)
        );
    }

    @Test
    @DisplayName("같은 상품이라도 이벤트 발생일이 다르면 서로 다른 상품 지표를 갱신한다.")
    void update_WhenSameProductDifferentDate_ShouldUpdateDifferentMetrics() {
        // given
        FakeProductMetricsRepository productMetricsRepository = new FakeProductMetricsRepository(
            ProductMetrics.create(LocalDate.of(2026, 7, 14), 1L)
        );
        MetricsUpdateService metricsUpdateService = new MetricsUpdateService(
            productMetricsRepository,
            new RankingScorePolicy()
        );

        // when
        metricsUpdateService.update(new ProductRankingEvent(
            "event-3",
            RankingEventType.VIEW,
            1L,
            BigDecimal.ZERO,
            0,
            LocalDateTime.of(2026, 7, 15, 10, 0)
        ));

        // then
        assertThat(productMetricsRepository.saved.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(productMetricsRepository.metrics).containsOnlyKeys(
            new MetricsKey(LocalDate.of(2026, 7, 14), 1L),
            new MetricsKey(LocalDate.of(2026, 7, 15), 1L)
        );
    }

    @Test
    @DisplayName("상품 삭제 이벤트는 상품 지표를 변경하지 않는다.")
    void update_WhenProductDeleted_ShouldNotUpdateMetrics() {
        // given
        FakeProductMetricsRepository productMetricsRepository = new FakeProductMetricsRepository();
        MetricsUpdateService metricsUpdateService = new MetricsUpdateService(
            productMetricsRepository,
            new RankingScorePolicy()
        );

        // when
        metricsUpdateService.update(new ProductRankingEvent(
            "event-4",
            RankingEventType.PRODUCT_DELETED,
            1L,
            BigDecimal.ZERO,
            0,
            LocalDateTime.of(2026, 7, 15, 10, 0)
        ));

        // then
        assertThat(productMetricsRepository.saved).isNull();
    }

    private static class FakeProductMetricsRepository implements ProductMetricsRepository {
        private final Map<MetricsKey, ProductMetrics> metrics = new HashMap<>();
        private ProductMetrics saved;

        private FakeProductMetricsRepository() {
        }

        private FakeProductMetricsRepository(ProductMetrics found) {
            this.metrics.put(new MetricsKey(found.getMetricDate(), found.getProductId()), found);
        }

        @Override
        public Optional<ProductMetrics> findByMetricDateAndProductId(LocalDate metricDate, Long productId) {
            return Optional.ofNullable(metrics.get(new MetricsKey(metricDate, productId)));
        }

        @Override
        public ProductMetrics save(ProductMetrics productMetrics) {
            this.saved = productMetrics;
            this.metrics.put(new MetricsKey(productMetrics.getMetricDate(), productMetrics.getProductId()), productMetrics);
            return productMetrics;
        }
    }

    private record MetricsKey(LocalDate metricDate, Long productId) {
    }
}
