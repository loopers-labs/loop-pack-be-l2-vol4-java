package com.loopers.tddstudy.infrastructure.batch;

import com.loopers.tddstudy.infrastructure.metrics.MetricSum;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetrics;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyDeltaProcessorTest {

    private final ProductMetricsDailyJpaRepository dailyRepository =
            mock(ProductMetricsDailyJpaRepository.class);
    private final LocalDate baseDate = LocalDate.of(2026, 7, 21);
    private final DailyDeltaProcessor processor =
            new DailyDeltaProcessor(dailyRepository, baseDate, false);   // 평상시 실행
    private final DailyDeltaProcessor baselineProcessor =
            new DailyDeltaProcessor(dailyRepository, baseDate, true);    // 최초 실행

    @Test
    void 델타는_누적값에서_이미_쌓인_합계를_뺀_값이다() throws Exception {
        // given: 누적 like=100, sales=50, view=600
        ProductMetrics metrics = new ProductMetrics(1L);
        metrics.addLike(100);
        metrics.addSales(50);
        metrics.addView(600);
        // daily에 이미 like=90, sales=40, view=500 쌓여있음
        when(dailyRepository.sumByProductId(1L)).thenReturn(new MetricSum(90, 40, 500));

        // when
        ProductMetricsDaily result = processor.process(metrics);

        // then: 차이만큼이 오늘 델타
        assertThat(result.getLikeCount()).isEqualTo(10);
        assertThat(result.getSalesCount()).isEqualTo(10);
        assertThat(result.getViewCount()).isEqualTo(100);
        assertThat(result.getMetricDate()).isEqualTo(baseDate);
        assertThat(result.getProductId()).isEqualTo(1L);
    }

    @Test
    void 최초_실행은_누적값을_집계창_밖의_기준일에_기록한다() throws Exception {
        // 6개월치 누적이 통째로 들어오는 상황
        ProductMetrics metrics = new ProductMetrics(1L);
        metrics.addLike(50_000);
        when(dailyRepository.sumByProductId(1L)).thenReturn(new MetricSum(0, 0, 0));

        ProductMetricsDaily result = baselineProcessor.process(metrics);

        // 값은 보존하되 (불변식 유지)
        assertThat(result.getLikeCount()).isEqualTo(50_000);
        // 오늘이 아니라 과거 기준일에 기록 → 어떤 주/월 집계에도 안 걸림
        assertThat(result.getMetricDate()).isEqualTo(DailyDeltaProcessor.BASELINE_DATE);
        assertThat(result.getMetricDate()).isNotEqualTo(baseDate);
    }

    @Test
    void 최초_실행이_아니면_오늘_날짜로_기록한다() throws Exception {
        ProductMetrics metrics = new ProductMetrics(1L);
        metrics.addLike(7);
        when(dailyRepository.sumByProductId(1L)).thenReturn(new MetricSum(0, 0, 0));

        ProductMetricsDaily result = processor.process(metrics);

        assertThat(result.getLikeCount()).isEqualTo(7);
        assertThat(result.getMetricDate()).isEqualTo(baseDate);
    }

    @Test
    void 변화가_없으면_저장하지_않는다() throws Exception {
        ProductMetrics metrics = new ProductMetrics(1L);
        metrics.addLike(50);
        // 이미 50 전부 쌓여있음 → 오늘 변화 0
        when(dailyRepository.sumByProductId(1L)).thenReturn(new MetricSum(50, 0, 0));

        ProductMetricsDaily result = processor.process(metrics);

        assertThat(result).isNull();   // 걸러짐
    }

    @Test
    void 좋아요가_취소되면_델타가_음수다() throws Exception {
        ProductMetrics metrics = new ProductMetrics(1L);
        metrics.addLike(30);
        // 어제까지 35였는데 오늘 5개 취소됨
        when(dailyRepository.sumByProductId(1L)).thenReturn(new MetricSum(35, 0, 0));

        ProductMetricsDaily result = processor.process(metrics);

        assertThat(result.getLikeCount()).isEqualTo(-5);
    }
}
