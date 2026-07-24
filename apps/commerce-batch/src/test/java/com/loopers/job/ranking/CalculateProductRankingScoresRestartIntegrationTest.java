package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.ranking.application.RankingCandidate;
import com.loopers.ranking.infrastructure.JdbcProductRankingCandidateRepository;
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
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false",
    "commerce.ranking.batch.chunk-size=1000"
})
@SpringBatchTest
@Import(CalculateProductRankingScoresRestartIntegrationTest.TestJobConfiguration.class)
class CalculateProductRankingScoresRestartIntegrationTest {

    private static final String TEST_JOB_NAME = "calculateProductRankingScoresRestartTestJob";
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
    private FailingCandidateRepository failingCandidateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

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
        insertMetrics();
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("두 번째 Chunk 실패 후 같은 JobInstance를 재시작하면 마지막 Commit 다음 상품부터 처리한다.")
    @Test
    void restartsAfterLastCommittedProduct() throws Exception {
        // arrange
        JobParameters jobParameters = jobParameters();

        // act
        JobExecution failedExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        StepExecution failedStep = failedExecution.getStepExecutions().iterator().next();
        assertAll(
            () -> assertThat(failedExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.FAILED.getExitCode()),
            () -> assertThat(countCandidates()).isEqualTo(1_000),
            () -> assertThat(failingCandidateRepository.batchSizes())
                .containsExactly(1_000, 1),
            () -> assertThat(failedStep.getSkipCount()).isZero()
        );

        // arrange for restart
        failingCandidateRepository.disableFailureAndClearAttempts();

        // act
        JobExecution restartedExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        StepExecution restartedStep = restartedExecution.getStepExecutions().iterator().next();
        Double minimumScore = jdbcTemplate.queryForObject(
            "select min(score) from product_rank_candidates",
            Double.class
        );
        Double maximumScore = jdbcTemplate.queryForObject(
            "select max(score) from product_rank_candidates",
            Double.class
        );
        assertAll(
            () -> assertThat(restartedExecution.getJobInstance())
                .isEqualTo(failedExecution.getJobInstance()),
            () -> assertThat(restartedExecution.getExitStatus())
                .isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(restartedStep.getReadCount()).isEqualTo(1),
            () -> assertThat(restartedStep.getWriteCount()).isEqualTo(1),
            () -> assertThat(failingCandidateRepository.batchSizes())
                .containsExactly(1),
            () -> assertThat(countCandidates()).isEqualTo(1_001),
            () -> assertThat(countPublishedRankings()).isZero(),
            () -> assertThat(minimumScore).isCloseTo(0.1, offset(1.0e-10)),
            () -> assertThat(maximumScore).isCloseTo(0.1, offset(1.0e-10))
        );
    }

    private JobParameters jobParameters() {
        return new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();
    }

    private void insertMetrics() {
        List<Object[]> batchArguments = LongStream.rangeClosed(1, 1_001)
            .mapToObj(productId -> new Object[]{
                LocalDate.of(2026, 7, 19),
                productId,
                1L,
                0L,
                0L,
                0L,
                LocalDateTime.of(2026, 7, 20, 2, 0)
            })
            .toList();
        jdbcTemplate.batchUpdate(
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
                values (?, ?, ?, ?, ?, ?, ?)
                """,
            batchArguments
        );
    }

    private long countCandidates() {
        return jdbcTemplate.queryForObject(
            "select count(*) from product_rank_candidates",
            Long.class
        );
    }

    private long countPublishedRankings() {
        return jdbcTemplate.queryForObject(
            "select count(*) from mv_product_rank_weekly",
            Long.class
        );
    }

    static class FailingCandidateRepository
        implements ProductRankingCandidateRepository {

        private final JdbcProductRankingCandidateRepository delegate;
        private final List<Integer> batchSizes = new ArrayList<>();
        private boolean failureEnabled = true;

        FailingCandidateRepository(JdbcProductRankingCandidateRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void upsertAll(List<? extends RankingCandidate> candidates) {
            batchSizes.add(candidates.size());
            boolean containsLastProduct = candidates.stream()
                .anyMatch(candidate -> candidate.productId() == 1_001L);
            if (failureEnabled && containsLastProduct) {
                throw new DataIntegrityViolationException("forced second chunk failure");
            }
            delegate.upsertAll(candidates);
        }

        @Override
        public long countCandidates(long snapshotId) {
            return delegate.countCandidates(snapshotId);
        }

        @Override
        public List<RankingCandidate> findTopCandidates(
            long snapshotId,
            int limit
        ) {
            return delegate.findTopCandidates(snapshotId, limit);
        }

        @Override
        public int deleteCandidates(long snapshotId, int limit) {
            return delegate.deleteCandidates(snapshotId, limit);
        }

        void disableFailureAndClearAttempts() {
            failureEnabled = false;
            batchSizes.clear();
        }

        List<Integer> batchSizes() {
            return List.copyOf(batchSizes);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJobConfiguration {

        @Bean
        @Primary
        FailingCandidateRepository failingCandidateRepository(
            JdbcProductRankingCandidateRepository delegate
        ) {
            return new FailingCandidateRepository(delegate);
        }

        @Bean(TEST_JOB_NAME)
        Job calculateProductRankingScoresRestartTestJob(
            JobRepository jobRepository,
            @Qualifier(ProductRankingSnapshotJobConfig.CALCULATE_STEP_NAME) Step calculateStep
        ) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository)
                .start(calculateStep)
                .build();
        }
    }
}
