package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.ranking.application.RankingCandidate;
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
import org.springframework.batch.core.Step;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
@Import(PublishProductRankingAtomicityIntegrationTest.TestJobConfiguration.class)
class PublishProductRankingAtomicityIntegrationTest {

    private static final String TEST_JOB_NAME = "publishProductRankingAtomicityTestJob";
    private static final ProductRankingSnapshotKey SNAPSHOT_KEY = new ProductRankingSnapshotKey(
        RankingPeriod.WEEKLY,
        LocalDate.of(2026, 7, 19),
        1
    );
    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-20T02:10:00Z");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(TEST_JOB_NAME)
    private Job testJob;

    @Autowired
    private FailingCompletionSnapshotRepository snapshotRepository;

    @Autowired
    private ProductRankingCandidateRepository candidateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(testJob);
        when(clock.instant()).thenReturn(COMPLETED_AT);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("완료 전환 충돌 시 순위를 롤백하고 같은 JobInstance 재시작에서 공개한다.")
    @Test
    void rollsBackRanksAndRestartsPublishStep() throws Exception {
        // arrange
        snapshotRepository.insert(new NewProductRankingSnapshot(
            SNAPSHOT_KEY,
            SCORE_POLICY,
            Instant.parse("2026-07-20T02:00:00Z")
        ));
        ProductRankingSnapshotHeader snapshot =
            snapshotRepository.findBy(SNAPSHOT_KEY).orElseThrow();
        insertMetric(101L);
        candidateRepository.upsertAll(
            List.of(new RankingCandidate(snapshot.id(), 101L, 5.3))
        );
        JobParameters parameters = jobParameters();

        // act
        JobExecution failedExecution = jobLauncherTestUtils.launchJob(parameters);

        // assert
        ProductRankingSnapshotHeader incompleteAfterFailure =
            snapshotRepository.findBy(SNAPSHOT_KEY).orElseThrow();
        assertAll(
            () -> assertThat(failedExecution.getStatus()).isEqualTo(BatchStatus.FAILED),
            () -> assertThat(candidateRepository.countCandidates(snapshot.id()))
                .isEqualTo(1),
            () -> assertThat(countPublishedRankings(snapshot.id())).isZero(),
            () -> assertThat(incompleteAfterFailure.completedAt()).isNull()
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
            () -> assertThat(candidateRepository.countCandidates(snapshot.id()))
                .isEqualTo(1),
            () -> assertThat(countPublishedRankings(snapshot.id())).isEqualTo(1),
            () -> assertThat(completed.completedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    private void insertMetric(long productId) {
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
                values ('2026-07-15', ?, 1, 0, 0, 0, ?)
                """,
            productId,
            LocalDateTime.of(2026, 7, 20, 2, 0)
        );
    }

    private long countPublishedRankings(long snapshotId) {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from mv_product_rank_weekly
                where snapshot_id = ?
                """,
            Long.class,
            snapshotId
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
        public java.util.Optional<ProductRankingSnapshotHeader> findBy(
            ProductRankingSnapshotKey key
        ) {
            return delegate.findBy(key);
        }

        @Override
        public Optional<ProductRankingSnapshotHeader> findById(
            long snapshotId
        ) {
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
    static class TestJobConfiguration {

        @Bean
        @Primary
        FailingCompletionSnapshotRepository failingCompletionSnapshotRepository(
            JdbcProductRankingSnapshotRepository delegate
        ) {
            return new FailingCompletionSnapshotRepository(delegate);
        }

        @Bean(TEST_JOB_NAME)
        Job publishProductRankingAtomicityTestJob(
            JobRepository jobRepository,
            @Qualifier(ProductRankingSnapshotJobConfig.PUBLISH_STEP_NAME) Step publishStep
        ) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository)
                .start(publishStep)
                .build();
        }
    }
}
