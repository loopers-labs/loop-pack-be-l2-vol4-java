package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
@Import(CalculateProductRankingScoresRetryIntegrationTest.TestJobConfiguration.class)
class CalculateProductRankingScoresRetryIntegrationTest {

    private static final String TEST_JOB_NAME = "calculateProductRankingScoresRetryTestJob";
    private static final ProductRankingSnapshotKey SNAPSHOT_KEY =
        new ProductRankingSnapshotKey(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 19),
            1
        );

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(TEST_JOB_NAME)
    private Job testJob;

    @Autowired
    private ProductRankingSnapshotRepository snapshotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private ProductRankingCandidateRepository candidateRepository;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(testJob);
        snapshotRepository.insert(
            new NewProductRankingSnapshot(
                SNAPSHOT_KEY,
                new RankingScorePolicy(0.1, 0.2, 0.7, 10_000),
                Instant.parse("2026-07-20T02:00:00Z")
            )
        );
        jdbcTemplate.update(
            """
                insert into product_metrics(
                    metric_date,
                    product_id,
                    view_count,
                    like_delta,
                    order_quantity,
                    order_amount,
                    updated_at
                )
                values ('2026-07-19', 101, 1, 0, 0, 0, ?)
                """,
            LocalDateTime.of(2026, 7, 20, 2, 0)
        );
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("DB Lock 경합은 최초 시도를 포함해 최대 3회 안에서 재시도한다.")
    @Test
    void retriesPessimisticLockFailure() throws Exception {
        // arrange
        stubTwoFailuresThenSuccess(new CannotAcquireLockException("lock"));

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

        // assert
        assertCompletedAfterThreeAttempts(execution);
    }

    @DisplayName("일시적인 DB 자원 오류는 최초 시도를 포함해 최대 3회 안에서 재시도한다.")
    @Test
    void retriesTransientDataAccessResourceFailure() throws Exception {
        // arrange
        stubTwoFailuresThenSuccess(
            new TransientDataAccessResourceException("resource")
        );

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

        // assert
        assertCompletedAfterThreeAttempts(execution);
    }

    @DisplayName("허용된 일시 오류도 3회 모두 실패하면 Chunk를 Rollback하고 Step을 실패시킨다.")
    @Test
    void failsAfterRetryLimitIsExhausted() throws Exception {
        // arrange
        doThrow(new CannotAcquireLockException("lock"))
            .when(candidateRepository)
            .upsertAll(anyList());

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

        // assert
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertAll(
            () -> assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.FAILED.getExitCode()),
            () -> verify(candidateRepository, times(3))
                .upsertAll(anyList()),
            () -> assertThat(stepExecution.getWriteCount()).isZero(),
            () -> assertThat(stepExecution.getSkipCount()).isZero()
        );
    }

    @DisplayName("Query Timeout은 재시도하거나 상품을 Skip하지 않고 Step을 실패시킨다.")
    @Test
    void failsImmediatelyOnQueryTimeout() throws Exception {
        // arrange
        doThrow(new QueryTimeoutException("timeout"))
            .when(candidateRepository)
            .upsertAll(anyList());

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

        // assert
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertAll(
            () -> assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.FAILED.getExitCode()),
            () -> verify(candidateRepository, times(1))
                .upsertAll(anyList()),
            () -> assertThat(stepExecution.getSkipCount()).isZero()
        );
    }

    private void stubTwoFailuresThenSuccess(RuntimeException exception) {
        doThrow(exception, exception)
            .doNothing()
            .when(candidateRepository)
            .upsertAll(anyList());
    }

    private void assertCompletedAfterThreeAttempts(JobExecution execution) {
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> verify(candidateRepository, times(3))
                .upsertAll(anyList()),
            () -> assertThat(stepExecution.getSkipCount()).isZero()
        );
    }

    private JobParameters jobParameters() {
        return new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJobConfiguration {

        @Bean(TEST_JOB_NAME)
        Job calculateProductRankingScoresRetryTestJob(
            JobRepository jobRepository,
            @Qualifier(ProductRankingSnapshotJobConfig.CALCULATE_STEP_NAME) Step calculateStep
        ) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository)
                .start(calculateStep)
                .build();
        }
    }
}
