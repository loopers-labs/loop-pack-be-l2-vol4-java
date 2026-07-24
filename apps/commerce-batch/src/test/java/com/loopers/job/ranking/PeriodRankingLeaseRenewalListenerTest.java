package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.PeriodRankingLeaseRenewalListener;
import com.loopers.batch.job.ranking.PeriodRankingLockManager;
import com.loopers.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PeriodRankingLeaseRenewalListenerTest {
    @DisplayName("aggregate chunk 시작마다 해당 기간의 owner lock heartbeat를 갱신한다.")
    @Test
    void renewsLeaseBeforeChunk() {
        PeriodRankingLockManager lockManager = mock(PeriodRankingLockManager.class);
        PeriodRankingLeaseRenewalListener listener = new PeriodRankingLeaseRenewalListener(
            lockManager,
            RankingPeriod.WEEKLY
        );

        listener.beforeChunk(chunkContext("2026-07-08", 77L));

        verify(lockManager).renew(LocalDate.of(2026, 7, 6), 77L);
    }

    @DisplayName("heartbeat가 ownership loss를 감지하면 chunk도 실패시킨다.")
    @Test
    void propagatesOwnershipLoss() {
        PeriodRankingLockManager lockManager = mock(PeriodRankingLockManager.class);
        PeriodRankingLeaseRenewalListener listener = new PeriodRankingLeaseRenewalListener(
            lockManager,
            RankingPeriod.WEEKLY
        );
        ChunkContext context = chunkContext("2026-07-08", 77L);
        doThrow(new IllegalStateException("ranking lock ownership lost"))
            .when(lockManager)
            .renew(LocalDate.of(2026, 7, 6), 77L);

        assertThatThrownBy(() -> listener.beforeChunk(context))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ownership");
    }

    private ChunkContext chunkContext(String requestDate, long jobExecutionId) {
        JobInstance jobInstance = new JobInstance(10L, "periodRankingJob");
        JobExecution jobExecution = new JobExecution(
            jobInstance,
            new JobParametersBuilder()
                .addString("requestDate", requestDate)
                .toJobParameters()
        );
        jobExecution.setId(jobExecutionId);
        StepExecution stepExecution = new StepExecution("aggregateStep", jobExecution);
        return new ChunkContext(new StepContext(stepExecution));
    }
}
