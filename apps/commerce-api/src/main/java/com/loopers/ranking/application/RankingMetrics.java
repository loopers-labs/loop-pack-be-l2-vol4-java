package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RankingMetrics {

    private static final String PAGE_LOOKUP_FAILURES = "ranking.page.lookup.failures.total";
    private static final String PUBLISHED_SNAPSHOT_MISSING =
        "ranking.published.snapshot.missing.total";
    private static final String PUBLISHED_SNAPSHOT_EMPTY =
        "ranking.published.snapshot.empty.total";

    private final MeterRegistry meterRegistry;

    public RankingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordPageLookupFailure(RankingPeriod period, RankingLookupStage stage) {
        meterRegistry.counter(
            PAGE_LOOKUP_FAILURES,
            "period",
            period.name(),
            "stage",
            stage.name()
        ).increment();
    }

    public void recordPublishedSnapshotMissing(RankingPeriod period) {
        meterRegistry.counter(
            PUBLISHED_SNAPSHOT_MISSING,
            "period",
            period.name()
        ).increment();
    }

    public void recordPublishedSnapshotEmpty(RankingPeriod period) {
        meterRegistry.counter(
            PUBLISHED_SNAPSHOT_EMPTY,
            "period",
            period.name()
        ).increment();
    }
}
