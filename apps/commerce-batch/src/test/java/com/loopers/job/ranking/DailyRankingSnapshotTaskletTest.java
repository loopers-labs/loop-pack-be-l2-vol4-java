package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.DailyRankingExecutionLock;
import com.loopers.batch.job.ranking.DailyRankingMetricReader;
import com.loopers.batch.job.ranking.DailyRankingSnapshotPublisher;
import com.loopers.batch.job.ranking.DailyRankingSnapshotTasklet;
import com.loopers.ranking.DailyRankingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyRankingSnapshotTaskletTest {

    @DisplayName("DB 조회 중 실패해도 획득한 동일 날짜 실행 lock을 해제한다.")
    @Test
    void releasesExecutionLockWhenBuildFails() {
        LocalDate date = LocalDate.now(DailyRankingKey.ZONE_ID).minusDays(1);
        DailyRankingMetricReader reader = mock(DailyRankingMetricReader.class);
        DailyRankingSnapshotPublisher publisher = mock(DailyRankingSnapshotPublisher.class);
        DailyRankingExecutionLock executionLock = mock(DailyRankingExecutionLock.class);
        DailyRankingSnapshotTasklet tasklet = new DailyRankingSnapshotTasklet(reader, publisher, executionLock);
        ChunkContext chunkContext = mock(ChunkContext.class, RETURNS_DEEP_STUBS);
        ReflectionTestUtils.setField(tasklet, "requestDate", date.toString());
        when(chunkContext.getStepContext().getStepExecution().getJobExecutionId()).thenReturn(10L);
        when(executionLock.acquire(date, 10L)).thenReturn(true);
        when(reader.read(date)).thenThrow(new IllegalStateException("injected read failure"));

        assertThatThrownBy(() -> tasklet.execute(null, chunkContext))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("injected read failure");

        verify(executionLock).release(date, 10L);
    }
}
