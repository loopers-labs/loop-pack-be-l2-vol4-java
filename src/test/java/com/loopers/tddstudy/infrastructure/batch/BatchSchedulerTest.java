package com.loopers.tddstudy.infrastructure.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class BatchSchedulerTest {

    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final Job dailySnapshotJob = mock(Job.class);
    private final Job rankAggregationJob = mock(Job.class);
    private final BatchScheduler scheduler =
            new BatchScheduler(jobLauncher, dailySnapshotJob, rankAggregationJob);

    private String today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    @DisplayName("스냅샷 스케줄은 오늘 날짜를 baseDate 로 넘겨 실행한다")
    void snapshot_launches_with_today_as_base_date() throws Exception {
        scheduler.runDailySnapshot();

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(dailySnapshotJob), captor.capture());
        assertThat(captor.getValue().getString("baseDate")).isEqualTo(today());
    }

    @Test
    @DisplayName("집계 스케줄은 집계 Job 을 오늘 날짜로 실행한다")
    void aggregation_launches_with_today_as_base_date() throws Exception {
        scheduler.runRankAggregation();

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(rankAggregationJob), captor.capture());
        assertThat(captor.getValue().getString("baseDate")).isEqualTo(today());
    }

    @Test
    @DisplayName("Job 실행이 실패해도 스케줄러는 예외를 밖으로 던지지 않는다")
    void swallows_exception_so_scheduler_survives() throws Exception {
        when(jobLauncher.run(any(), any())).thenThrow(new IllegalStateException("배치 실패"));

        assertThatCode(() -> scheduler.runDailySnapshot()).doesNotThrowAnyException();
    }
}
