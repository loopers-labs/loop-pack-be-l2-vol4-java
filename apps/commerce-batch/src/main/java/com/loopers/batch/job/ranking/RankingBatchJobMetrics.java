package com.loopers.batch.job.ranking;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 주간/월간 랭킹 배치 Job의 최소 모니터링 메트릭(§3.4, PR419의 P1/P3만 채택).
 * P2(stale snapshot age)는 MV 조회 쪽 계측이 필요해 이번 범위에서 제외한다.
 */
@Component
public class RankingBatchJobMetrics {

    private final Counter failureCounter;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0);

    public RankingBatchJobMetrics(MeterRegistry meterRegistry) {
        this.failureCounter = Counter.builder("batch.rank.job.failure.count")
            .description("주간/월간 랭킹 배치 Job 실패 횟수")
            .register(meterRegistry);
        Gauge.builder("batch.rank.job.last.success.epoch", lastSuccessEpochSeconds, AtomicLong::get)
            .description("주간/월간 랭킹 배치 Job 마지막 성공 시각(epoch seconds)")
            .register(meterRegistry);
    }

    public void recordFailure() {
        failureCounter.increment();
    }

    public void recordSuccess(Instant successAt) {
        lastSuccessEpochSeconds.set(successAt.getEpochSecond());
    }
}
