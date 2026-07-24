package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductMetricAggregate;
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
import org.springframework.batch.item.database.JdbcPagingItemReader;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false",
    "spring.main.allow-bean-definition-overriding=true"
})
@SpringBatchTest
@Import(ProductMetricAggregateReaderRetryIntegrationTest.TestJobConfiguration.class)
class ProductMetricAggregateReaderRetryIntegrationTest {

    private static final String TEST_JOB_NAME = "productMetricAggregateReaderRetryTestJob";
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

    @MockitoBean(name = ProductRankingSnapshotJobConfig.PRODUCT_METRIC_AGGREGATE_READER_NAME)
    private JdbcPagingItemReader<ProductMetricAggregate> reader;

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
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("Reader의 일시적인 DB 오류도 최대 3회 안에서 재시도한다.")
    @Test
    void retriesTransientReaderFailure() throws Exception {
        // arrange
        when(reader.read())
            .thenThrow(new CannotAcquireLockException("lock"))
            .thenThrow(new TransientDataAccessResourceException("resource"))
            .thenReturn(new ProductMetricAggregate(101L, 30, 1, 30_000))
            .thenReturn(null);

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

        // assert
        Long candidateCount = jdbcTemplate.queryForObject(
            "select count(*) from product_rank_candidates",
            Long.class
        );
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> verify(reader, times(4)).read(),
            () -> assertThat(candidateCount).isEqualTo(1)
        );
    }

    @DisplayName("Reader의 Query Timeout은 재시도하거나 상품을 Skip하지 않고 실패한다.")
    @Test
    void failsImmediatelyOnReaderQueryTimeout() throws Exception {
        // arrange
        when(reader.read()).thenThrow(new QueryTimeoutException("timeout"));

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

        // assert
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertAll(
            () -> assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.FAILED.getExitCode()),
            () -> verify(reader, times(1)).read(),
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
        Job productMetricAggregateReaderRetryTestJob(
            JobRepository jobRepository,
            @Qualifier(ProductRankingSnapshotJobConfig.CALCULATE_STEP_NAME) Step calculateStep
        ) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository)
                .start(calculateStep)
                .build();
        }
    }
}
