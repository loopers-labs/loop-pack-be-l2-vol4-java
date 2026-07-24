package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingAssignment;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingResultRepository;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.ranking.infrastructure.JdbcProductRankingSnapshotRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
@Import(ProductRankingSnapshotJobRestartIntegrationTest.RestartTestConfiguration.class)
class ProductRankingSnapshotJobRestartIntegrationTest {

    private static final ProductRankingSnapshotKey SNAPSHOT_KEY = new ProductRankingSnapshotKey(
        RankingPeriod.WEEKLY,
        LocalDate.of(2026, 7, 19),
        1
    );
    private static final Instant CREATED_AT = Instant.parse("2026-07-20T02:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-20T02:10:00Z");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(ProductRankingSnapshotJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private FailingCompletionSnapshotRepository snapshotRepository;

    @Autowired
    private ProductRankingCandidateRepository candidateRepository;

    @Autowired
    private ProductRankingResultRepository resultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        when(clock.instant()).thenReturn(CREATED_AT, COMPLETED_AT, COMPLETED_AT);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("Publish 실패 후 같은 JobInstance를 재시작하면 완료된 앞 Step을 건너뛴다.")
    @Test
    void restartsFromFailedPublishStep() throws Exception {
        // arrange
        insertMetric();
        JobParameters parameters = jobParameters();

        // act
        JobExecution failedExecution = jobLauncherTestUtils.launchJob(parameters);

        // assert
        ProductRankingSnapshotHeader incomplete =
            snapshotRepository.findBy(SNAPSHOT_KEY).orElseThrow();
        assertAll(
            () -> assertThat(failedExecution.getStatus()).isEqualTo(BatchStatus.FAILED),
            () -> assertThat(failedExecution.getStepExecutions())
                .extracting(StepExecution::getStepName, StepExecution::getStatus)
                .containsExactly(
                    tuple(
                        ProductRankingSnapshotJobConfig.PREPARE_STEP_NAME,
                        BatchStatus.COMPLETED
                    ),
                    tuple(
                        ProductRankingSnapshotJobConfig.CALCULATE_STEP_NAME,
                        BatchStatus.COMPLETED
                    ),
                    tuple(
                        ProductRankingSnapshotJobConfig.PUBLISH_STEP_NAME,
                        BatchStatus.FAILED
                    )
                ),
            () -> assertThat(candidateRepository.countCandidates(incomplete.id()))
                .isEqualTo(1),
            () -> assertThat(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                incomplete.id(),
                101
            )).isEmpty(),
            () -> assertThat(incomplete.completedAt()).isNull()
        );

        // act
        snapshotRepository.allowCompletion();
        JobExecution restartedExecution = jobLauncherTestUtils.launchJob(parameters);

        // assert
        ProductRankingSnapshotHeader completed =
            snapshotRepository.findBy(SNAPSHOT_KEY).orElseThrow();
        assertAll(
            () -> assertThat(restartedExecution.getExitStatus())
                .isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(restartedExecution.getJobInstance().getInstanceId())
                .isEqualTo(failedExecution.getJobInstance().getInstanceId()),
            () -> assertThat(restartedExecution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly(ProductRankingSnapshotJobConfig.PUBLISH_STEP_NAME),
            () -> assertThat(resultRepository.findPublishedRankings(
                RankingPeriod.WEEKLY,
                completed.id(),
                101
            )).containsExactly(new ProductRankingAssignment(101L, 0.1, 1)),
            () -> assertThat(completed.completedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    private void insertMetric() {
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
                values ('2026-07-15', 101, 1, 0, 0, 0, ?)
                """,
            LocalDateTime.of(2026, 7, 20, 2, 0)
        );
    }

    private JobParameters jobParameters() {
        return new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();
    }

    static class FailingCompletionSnapshotRepository
        implements ProductRankingSnapshotRepository {

        private final JdbcProductRankingSnapshotRepository delegate;
        private boolean completionAllowed;

        FailingCompletionSnapshotRepository(
            JdbcProductRankingSnapshotRepository delegate
        ) {
            this.delegate = delegate;
        }

        @Override
        public Optional<ProductRankingSnapshotHeader> findBy(
            ProductRankingSnapshotKey key
        ) {
            return delegate.findBy(key);
        }

        @Override
        public Optional<ProductRankingSnapshotHeader> findById(long snapshotId) {
            return delegate.findById(snapshotId);
        }

        @Override
        public boolean existsNewerCompletedThan(ProductRankingSnapshotKey key) {
            return delegate.existsNewerCompletedThan(key);
        }

        @Override
        public void insert(NewProductRankingSnapshot snapshot) {
            delegate.insert(snapshot);
        }

        @Override
        public boolean completeIfIncomplete(long snapshotId, Instant completedAt) {
            if (!completionAllowed) {
                return false;
            }
            return delegate.completeIfIncomplete(snapshotId, completedAt);
        }

        void allowCompletion() {
            completionAllowed = true;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RestartTestConfiguration {

        @Bean
        @Primary
        FailingCompletionSnapshotRepository failingCompletionSnapshotRepository(
            JdbcProductRankingSnapshotRepository delegate
        ) {
            return new FailingCompletionSnapshotRepository(delegate);
        }
    }
}
