package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RankingMetrics rankingMetrics = new RankingMetrics(meterRegistry);

    @DisplayName("Ranking Page 조회 실패를 기록하면 기간과 실패 단계별 Counter가 증가한다")
    @Test
    void incrementsFailureCounter_whenPageLookupFails() {
        // act
        rankingMetrics.recordPageLookupFailure(
            RankingPeriod.WEEKLY,
            RankingLookupStage.PRODUCT_ENRICHMENT
        );

        // assert
        assertThat(meterRegistry.get("ranking.page.lookup.failures.total")
            .tag("period", "WEEKLY")
            .tag("stage", "PRODUCT_ENRICHMENT")
            .counter()
            .count())
            .isEqualTo(1.0);
    }

    @DisplayName("공개 Snapshot 조회 결과를 완료본 부재와 정상 빈 결과로 나누어 기록한다")
    @Test
    void recordsPublishedSnapshotMissingAndEmptySeparately() {
        // act
        rankingMetrics.recordPublishedSnapshotMissing(RankingPeriod.WEEKLY);
        rankingMetrics.recordPublishedSnapshotEmpty(RankingPeriod.MONTHLY);

        // assert
        assertThat(meterRegistry.get("ranking.published.snapshot.missing.total")
            .tag("period", "WEEKLY")
            .counter()
            .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ranking.published.snapshot.empty.total")
            .tag("period", "MONTHLY")
            .counter()
            .count()).isEqualTo(1.0);
    }
}
