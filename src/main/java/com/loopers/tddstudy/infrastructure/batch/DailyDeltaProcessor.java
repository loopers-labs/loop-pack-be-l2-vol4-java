package com.loopers.tddstudy.infrastructure.batch;

import com.loopers.tddstudy.infrastructure.metrics.MetricSum;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetrics;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import org.springframework.batch.item.ItemProcessor;

import java.time.LocalDate;

public class DailyDeltaProcessor implements ItemProcessor<ProductMetrics, ProductMetricsDaily> {

    /**
     * 최초 실행이 흡수하는 '언제 쌓였는지 알 수 없는 누적분'을 기록할 날짜.
     * 어떤 주간·월간 집계 창에도 절대 포함되지 않는 과거 시점이어야 한다.
     */
    static final LocalDate BASELINE_DATE = LocalDate.of(1970, 1, 1);

    private final ProductMetricsDailyJpaRepository dailyRepository;
    private final LocalDate baseDate;
    private final boolean baselineRun;

    public DailyDeltaProcessor(ProductMetricsDailyJpaRepository dailyRepository,
                               LocalDate baseDate,
                               boolean baselineRun) {
        this.dailyRepository = dailyRepository;
        this.baseDate = baseDate;
        this.baselineRun = baselineRun;
    }

    @Override
    public ProductMetricsDaily process(ProductMetrics metrics) {
        MetricSum previous = dailyRepository.sumByProductId(metrics.getProductId());

        long likeDelta  = metrics.getLikeCount()  - previous.likeSum();
        long salesDelta = metrics.getSalesCount() - previous.salesSum();
        long viewDelta  = metrics.getViewCount()  - previous.viewSum();

        // 오늘 아무 변화 없는 상품은 저장하지 않음 (null 반환 = 걸러냄)
        if (likeDelta == 0 && salesDelta == 0 && viewDelta == 0) {
            return null;
        }

        // 최초 실행분은 배치 이전에 쌓인 역사이므로, 집계 창 밖(BASELINE_DATE)에 남긴다.
        // 값은 버리지 않아야 Σ(daily) = 누적 불변식이 유지된다.
        LocalDate metricDate = baselineRun ? BASELINE_DATE : baseDate;

        return new ProductMetricsDaily(
                metrics.getProductId(), metricDate, likeDelta, salesDelta, viewDelta);
    }
}
