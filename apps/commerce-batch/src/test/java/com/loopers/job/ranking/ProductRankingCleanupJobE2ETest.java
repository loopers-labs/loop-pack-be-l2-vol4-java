package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingCleanupJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingAssignment;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.ranking.application.RankingCandidate;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingCleanupJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
class ProductRankingCleanupJobE2ETest {

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
    private ProductRankingCandidateRepository candidateRepository;

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

    @DisplayName("같은 기간의 다음 완료본이 있으면 이전 Snapshot의 후보만 삭제하고 공개 순위는 유지한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void deletesObsoleteCandidatesAndPreservesPublishedRankings(
        RankingPeriod period
    ) throws Exception {
        // arrange
        ProductRankingSnapshotHeader target = insertCompletedSnapshot(
            period,
            LocalDate.of(2026, 7, 12),
            1
        );
        ProductRankingSnapshotHeader latest = insertCompletedSnapshot(
            period,
            LocalDate.of(2026, 7, 19),
            1
        );
        candidateRepository.upsertAll(List.of(
            new RankingCandidate(target.id(), 101L, 10.0),
            new RankingCandidate(target.id(), 202L, 7.0),
            new RankingCandidate(target.id(), 303L, 5.0),
            new RankingCandidate(latest.id(), 404L, 3.0)
        ));
        ProductRankingAssignment published =
            new ProductRankingAssignment(101L, 10.0, 1);
        insertPublishedRanking(period, target.id(), published);
        List<ProductRankingAssignment> publishedBeforeCleanup =
            findPublishedRankings(period, target.id());

        // act
        JobExecution execution =
            jobLauncherTestUtils.launchJob(jobParameters(target.id()));

        // assert
        ProductRankingSnapshotHeader targetAfterCleanup =
            snapshotRepository.findById(target.id()).orElseThrow();
        assertAll(
            () -> assertThat(execution.getExitStatus())
                .isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly(
                    ProductRankingCleanupJobConfig.DELETE_OBSOLETE_CANDIDATES_STEP_NAME
                ),
            () -> assertThat(candidateRepository.countCandidates(target.id()))
                .isZero(),
            () -> assertThat(findPublishedRankings(period, target.id()))
                .isEqualTo(publishedBeforeCleanup),
            () -> assertThat(candidateRepository.countCandidates(latest.id()))
                .isEqualTo(1),
            () -> assertThat(targetAfterCleanup.completedAt())
                .isEqualTo(COMPLETED_AT)
        );
    }

    @DisplayName("최신 완료 Snapshot을 대상으로 받으면 후보를 삭제하기 전에 실패한다.")
    @Test
    void rejectsLatestCompletedSnapshot() throws Exception {
        // arrange
        ProductRankingSnapshotHeader latest = insertCompletedSnapshot(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 19),
            1
        );
        candidateRepository.upsertAll(List.of(
            new RankingCandidate(latest.id(), 101L, 10.0)
        ));

        // act
        JobExecution execution =
            jobLauncherTestUtils.launchJob(jobParameters(latest.id()));

        // assert
        assertAll(
            () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED),
            () -> assertThat(candidateRepository.countCandidates(latest.id()))
                .isEqualTo(1),
            () -> assertThat(
                snapshotRepository.findById(latest.id()).orElseThrow().completedAt()
            ).isEqualTo(COMPLETED_AT)
        );
    }

    @DisplayName("미완성 Snapshot을 대상으로 받으면 후보를 삭제하기 전에 실패한다.")
    @Test
    void rejectsIncompleteSnapshot() throws Exception {
        // arrange
        ProductRankingSnapshotHeader incomplete = insertIncompleteSnapshot(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 12),
            1
        );
        insertCompletedSnapshot(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 19),
            1
        );
        candidateRepository.upsertAll(List.of(
            new RankingCandidate(incomplete.id(), 101L, 10.0)
        ));

        // act
        JobExecution execution =
            jobLauncherTestUtils.launchJob(jobParameters(incomplete.id()));

        // assert
        assertAll(
            () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED),
            () -> assertThat(candidateRepository.countCandidates(incomplete.id()))
                .isEqualTo(1),
            () -> assertThat(
                snapshotRepository.findById(incomplete.id()).orElseThrow().completedAt()
            ).isNull()
        );
    }

    @DisplayName("이미 후보가 없는 유효한 과거 Snapshot은 멱등하게 성공한다.")
    @Test
    void completesWhenEligibleTargetHasNoCandidate() throws Exception {
        // arrange
        ProductRankingSnapshotHeader target = insertCompletedSnapshot(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 12),
            1
        );
        insertCompletedSnapshot(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 19),
            1
        );

        // act
        JobExecution execution =
            jobLauncherTestUtils.launchJob(jobParameters(target.id()));

        // assert
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    private ProductRankingSnapshotHeader insertCompletedSnapshot(
        RankingPeriod period,
        LocalDate aggregationEndDate,
        int revision
    ) {
        ProductRankingSnapshotHeader snapshot =
            insertIncompleteSnapshot(period, aggregationEndDate, revision);
        snapshotRepository.completeIfIncomplete(snapshot.id(), COMPLETED_AT);
        return snapshotRepository.findById(snapshot.id()).orElseThrow();
    }

    private ProductRankingSnapshotHeader insertIncompleteSnapshot(
        RankingPeriod period,
        LocalDate aggregationEndDate,
        int revision
    ) {
        ProductRankingSnapshotKey key = new ProductRankingSnapshotKey(
            period,
            aggregationEndDate,
            revision
        );
        snapshotRepository.insert(new NewProductRankingSnapshot(
            key,
            SCORE_POLICY,
            CREATED_AT
        ));
        return snapshotRepository.findBy(key).orElseThrow();
    }

    private JobParameters jobParameters(long targetSnapshotId) {
        return new JobParametersBuilder()
            .addLong("targetSnapshotId", targetSnapshotId)
            .toJobParameters();
    }

    private void insertPublishedRanking(
        RankingPeriod period,
        long snapshotId,
        ProductRankingAssignment ranking
    ) {
        jdbcTemplate.update(
            "insert into " + tableName(period)
                + "(snapshot_id, product_id, rank_no, score) values (?, ?, ?, ?)",
            snapshotId,
            ranking.productId(),
            ranking.rankNo(),
            ranking.score()
        );
    }

    private List<ProductRankingAssignment> findPublishedRankings(
        RankingPeriod period,
        long snapshotId
    ) {
        return jdbcTemplate.query(
            """
                select product_id, score, rank_no
                from %s
                where snapshot_id = ?
                order by rank_no
                """.formatted(tableName(period)),
            (resultSet, rowNumber) -> new ProductRankingAssignment(
                resultSet.getLong("product_id"),
                resultSet.getDouble("score"),
                resultSet.getInt("rank_no")
            ),
            snapshotId
        );
    }

    private String tableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }
}
