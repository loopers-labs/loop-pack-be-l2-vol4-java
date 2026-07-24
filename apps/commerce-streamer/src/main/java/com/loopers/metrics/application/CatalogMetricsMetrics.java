package com.loopers.metrics.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CatalogMetricsMetrics {

    private final DistributionSummary batchRecordSummary;
    private final DistributionSummary newEventSummary;
    private final DistributionSummary duplicateEventSummary;
    private final DistributionSummary dailyProductGroupSummary;
    private final DistributionSummary hourlyGroupSummary;
    private final Timer batchProcessingTimer;
    private final Counter batchFailureCounter;

    public CatalogMetricsMetrics(MeterRegistry meterRegistry) {
        this.batchRecordSummary = summary(
            meterRegistry,
            "catalog.metrics.batch.records",
            "Catalog Metrics Consumer가 Poll에서 받은 Record 수"
        );
        this.newEventSummary = summary(
            meterRegistry,
            "catalog.metrics.batch.new.events",
            "중복 제거 후 집계 대상이 된 신규 이벤트 수"
        );
        this.duplicateEventSummary = summary(
            meterRegistry,
            "catalog.metrics.batch.duplicate.events",
            "이미 처리되어 SOT 반영에서 제외한 이벤트 수"
        );
        this.dailyProductGroupSummary = summary(
            meterRegistry,
            "catalog.metrics.batch.product.groups",
            "Daily Product Metric으로 선집계한 날짜와 상품 그룹 수"
        );
        this.hourlyGroupSummary = summary(
            meterRegistry,
            "catalog.metrics.batch.hourly.groups",
            "Hourly Metric으로 선집계한 상품과 시간 Window 그룹 수"
        );
        this.batchProcessingTimer = Timer.builder("catalog.metrics.batch.processing.time")
            .description("Catalog Metrics Kafka Batch 전체 처리 시간")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        this.batchFailureCounter = Counter.builder("catalog.metrics.batch.failures.total")
            .description("Catalog Metrics Kafka Batch 처리 실패 횟수")
            .register(meterRegistry);
    }

    public void recordBatchRecords(int recordCount) {
        batchRecordSummary.record(recordCount);
    }

    public void recordAggregation(
        int eventCount,
        int newEventCount,
        int dailyProductGroupCount,
        int hourlyGroupCount
    ) {
        newEventSummary.record(newEventCount);
        duplicateEventSummary.record(eventCount - newEventCount);
        dailyProductGroupSummary.record(dailyProductGroupCount);
        hourlyGroupSummary.record(hourlyGroupCount);
    }

    public void recordBatchDuration(long durationNanos) {
        batchProcessingTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordBatchFailure() {
        batchFailureCounter.increment();
    }

    private DistributionSummary summary(
        MeterRegistry meterRegistry,
        String name,
        String description
    ) {
        return DistributionSummary.builder(name)
            .description(description)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }
}
