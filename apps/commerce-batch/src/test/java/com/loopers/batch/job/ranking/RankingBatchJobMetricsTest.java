package com.loopers.batch.job.ranking;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RankingBatchJobMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RankingBatchJobMetrics metrics = new RankingBatchJobMetrics(meterRegistry);

    @DisplayName("recordFailure()를 호출할 때,")
    @Nested
    class RecordFailure {

        @DisplayName("실패 카운터가 1씩 증가한다.")
        @Test
        void incrementsFailureCounter() {
            metrics.recordFailure();
            metrics.recordFailure();

            double count = meterRegistry.get("batch.rank.job.failure.count").counter().count();
            assertThat(count).isEqualTo(2.0);
        }
    }

    @DisplayName("recordSuccess()를 호출할 때,")
    @Nested
    class RecordSuccess {

        @DisplayName("마지막 성공 시각 게이지가 해당 epoch 초로 갱신된다.")
        @Test
        void updatesLastSuccessGauge() {
            Instant successAt = Instant.ofEpochSecond(1_800_000_000L);

            metrics.recordSuccess(successAt);

            double gaugeValue = meterRegistry.get("batch.rank.job.last.success.epoch").gauge().value();
            assertThat(gaugeValue).isEqualTo(1_800_000_000.0);
        }
    }
}
