package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingCleanupJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.ranking.application.RankingCandidate;
import com.loopers.ranking.infrastructure.JdbcProductRankingCandidateRepository;
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
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingCleanupJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
@Import(ProductRankingCleanupJobRestartIntegrationTest.RestartTestConfiguration.class)
class ProductRankingCleanupJobRestartIntegrationTest {

    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
    private static final Instant CREATED_AT = Instant.parse("2026-07-20T02:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-20T02:10:00Z");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(ProductRankingCleanupJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductRankingSnapshotRepository snapshotRepository;

    @Autowired
    private FailingCleanupCandidateRepository candidateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("일부 DELETE 커밋 후 실패하면 같은 JobInstance 재시작 시 남은 후보만 삭제한다.")
    @Test
    void restartsWithRemainingCandidatesAfterCommittedDelete() throws Exception {
        // arrange
        ProductRankingSnapshotHeader target = insertCompletedSnapshot(
            LocalDate.of(2026, 7, 12)
        );
        insertCompletedSnapshot(LocalDate.of(2026, 7, 19));
        List<RankingCandidate> candidates = LongStream.rangeClosed(1, 1_001)
            .mapToObj(productId -> new RankingCandidate(
                target.id(),
                productId,
                productId
            ))
            .toList();
        candidateRepository.upsertAll(candidates);
        insertPublishedRanking(target.id());

        JobParameters parameters = jobParameters(target.id());

        // act
        JobExecution failedExecution =
            jobLauncherTestUtils.launchJob(parameters);

        // assert
        assertAll(
            () -> assertThat(failedExecution.getStatus())
                .isEqualTo(BatchStatus.FAILED),
            () -> assertThat(candidateRepository.countCandidates(target.id()))
                .isEqualTo(1),
            () -> assertThat(countPublishedRankings(target.id())).isEqualTo(1)
        );

        // act
        candidateRepository.allowDeletes();
        JobExecution restartedExecution =
            jobLauncherTestUtils.launchJob(parameters);

        // assert
        assertAll(
            () -> assertThat(restartedExecution.getExitStatus())
                .isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(restartedExecution.getJobInstance().getInstanceId())
                .isEqualTo(failedExecution.getJobInstance().getInstanceId()),
            () -> assertThat(candidateRepository.countCandidates(target.id()))
                .isZero(),
            () -> assertThat(countPublishedRankings(target.id())).isEqualTo(1),
            () -> assertThat(
                snapshotRepository.findById(target.id()).orElseThrow().completedAt()
            ).isEqualTo(COMPLETED_AT)
        );
    }

    private ProductRankingSnapshotHeader insertCompletedSnapshot(
        LocalDate aggregationEndDate
    ) {
        ProductRankingSnapshotKey key = new ProductRankingSnapshotKey(
            RankingPeriod.WEEKLY,
            aggregationEndDate,
            1
        );
        snapshotRepository.insert(new NewProductRankingSnapshot(
            key,
            SCORE_POLICY,
            CREATED_AT
        ));
        ProductRankingSnapshotHeader snapshot =
            snapshotRepository.findBy(key).orElseThrow();
        snapshotRepository.completeIfIncomplete(snapshot.id(), COMPLETED_AT);
        return snapshotRepository.findById(snapshot.id()).orElseThrow();
    }

    private JobParameters jobParameters(long targetSnapshotId) {
        return new JobParametersBuilder()
            .addLong("targetSnapshotId", targetSnapshotId)
            .toJobParameters();
    }

    private void insertPublishedRanking(long snapshotId) {
        jdbcTemplate.update(
            """
                insert into mv_product_rank_weekly(
                    snapshot_id,
                    product_id,
                    rank_no,
                    score
                )
                values (?, 1, 1, 1.0)
                """,
            snapshotId
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

    static class FailingCleanupCandidateRepository
        implements ProductRankingCandidateRepository {

        private final JdbcProductRankingCandidateRepository delegate;
        private int deleteAttempts;
        private boolean failureEnabled = true;

        FailingCleanupCandidateRepository(
            JdbcProductRankingCandidateRepository delegate
        ) {
            this.delegate = delegate;
        }

        @Override
        public void upsertAll(List<? extends RankingCandidate> candidates) {
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
            deleteAttempts++;
            if (failureEnabled && deleteAttempts == 2) {
                throw new TransientDataAccessResourceException(
                    "forced second delete failure"
                );
            }
            return delegate.deleteCandidates(snapshotId, limit);
        }

        void allowDeletes() {
            failureEnabled = false;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RestartTestConfiguration {

        @Bean
        @Primary
        FailingCleanupCandidateRepository failingCleanupCandidateRepository(
            JdbcProductRankingCandidateRepository delegate
        ) {
            return new FailingCleanupCandidateRepository(delegate);
        }
    }
}
