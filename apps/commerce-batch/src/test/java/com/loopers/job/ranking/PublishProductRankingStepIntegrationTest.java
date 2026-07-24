package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingAssignment;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingResultRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
@Import(PublishProductRankingStepIntegrationTest.TestJobConfiguration.class)
class PublishProductRankingStepIntegrationTest {

    private static final String TEST_JOB_NAME = "publishProductRankingStepTestJob";
    private static final Instant CREATED_AT = Instant.parse("2026-07-20T02:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-20T02:10:00.123456Z");
    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);

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
        jobLauncherTestUtils.setJob(testJob);
        when(clock.instant()).thenReturn(COMPLETED_AT);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주간·월간 후보를 점수와 상품 ID 순으로 확정해 공개한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void publishesRankingsInDeterministicOrder(RankingPeriod period) throws Exception {
        // arrange
        ProductRankingSnapshotHeader snapshot = insertSnapshot(period);
        insertMetric(303L);
        insertMetric(401L);
        insertMetric(402L);
        candidateRepository.upsertAll(List.of(
            new RankingCandidate(snapshot.id(), 402L, 5.3),
            new RankingCandidate(snapshot.id(), 303L, 10.0),
            new RankingCandidate(snapshot.id(), 401L, 5.3)
        ));

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        List<ProductRankingAssignment> rankings =
            resultRepository.findPublishedRankings(period, snapshot.id(), 101);
        ProductRankingSnapshotHeader completed =
            snapshotRepository.findBy(snapshot.key()).orElseThrow();
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly(ProductRankingSnapshotJobConfig.PUBLISH_STEP_NAME),
            () -> assertThat(rankings).containsExactly(
                new ProductRankingAssignment(303L, 10.0, 1),
                new ProductRankingAssignment(401L, 5.3, 2),
                new ProductRankingAssignment(402L, 5.3, 3)
            ),
            () -> assertThat(completed.completedAt()).isEqualTo(COMPLETED_AT),
            () -> assertThat(countRows(otherTableName(period))).isZero()
        );
    }

    @DisplayName("원천 상품보다 후보가 적으면 순위를 부여하지 않고 Snapshot을 비공개로 남긴다.")
    @Test
    void leavesSnapshotIncomplete_whenCandidateIsMissing() throws Exception {
        // arrange
        RankingPeriod period = RankingPeriod.WEEKLY;
        ProductRankingSnapshotHeader snapshot = insertSnapshot(period);
        insertMetric(101L);
        insertMetric(202L);
        candidateRepository.upsertAll(
            List.of(new RankingCandidate(snapshot.id(), 101L, 5.3))
        );

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        ProductRankingSnapshotHeader incomplete =
            snapshotRepository.findBy(snapshot.key()).orElseThrow();
        assertAll(
            () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED),
            () -> assertThat(resultRepository.findPublishedRankings(
                period,
                snapshot.id(),
                101
            )).isEmpty(),
            () -> assertThat(incomplete.completedAt()).isNull()
        );
    }

    @DisplayName("원천 상품보다 후보가 많아도 순위를 부여하지 않고 Snapshot을 비공개로 남긴다.")
    @Test
    void leavesSnapshotIncomplete_whenCandidateIsUnexpectedlyAdded() throws Exception {
        // arrange
        RankingPeriod period = RankingPeriod.WEEKLY;
        ProductRankingSnapshotHeader snapshot = insertSnapshot(period);
        insertMetric(101L);
        candidateRepository.upsertAll(List.of(
            new RankingCandidate(snapshot.id(), 101L, 5.3),
            new RankingCandidate(snapshot.id(), 202L, 3.0)
        ));

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        ProductRankingSnapshotHeader incomplete =
            snapshotRepository.findBy(snapshot.key()).orElseThrow();
        assertAll(
            () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED),
            () -> assertThat(resultRepository.findPublishedRankings(
                period,
                snapshot.id(),
                101
            )).isEmpty(),
            () -> assertThat(incomplete.completedAt()).isNull()
        );
    }

    @DisplayName("101개 후보 중 점수가 높은 100개만 공개 MV에 저장한다.")
    @Test
    void publishesOnlyTopOneHundredCandidates() throws Exception {
        // arrange
        RankingPeriod period = RankingPeriod.WEEKLY;
        ProductRankingSnapshotHeader snapshot = insertSnapshot(period);
        List<RankingCandidate> candidates = new ArrayList<>();
        for (long productId = 1; productId <= 101; productId++) {
            insertMetric(productId);
            candidates.add(new RankingCandidate(
                snapshot.id(),
                productId,
                102 - productId
            ));
        }
        candidateRepository.upsertAll(candidates);

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        List<ProductRankingAssignment> rankings =
            resultRepository.findPublishedRankings(period, snapshot.id(), 101);
        Long excludedCandidateCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from mv_product_rank_weekly
                where snapshot_id = ?
                  and product_id = 101
                """,
            Long.class,
            snapshot.id()
        );
        Long publishedCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from mv_product_rank_weekly
                where snapshot_id = ?
                """,
            Long.class,
            snapshot.id()
        );
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(rankings).hasSize(100),
            () -> assertThat(rankings.getFirst())
                .isEqualTo(new ProductRankingAssignment(1L, 101.0, 1)),
            () -> assertThat(rankings.getLast())
                .isEqualTo(new ProductRankingAssignment(100L, 2.0, 100)),
            () -> assertThat(candidateRepository.countCandidates(snapshot.id()))
                .isEqualTo(101),
            () -> assertThat(publishedCount).isEqualTo(100),
            () -> assertThat(excludedCandidateCount).isZero()
        );
    }

    @DisplayName("원천과 후보가 모두 없으면 상세 행 없는 완료 Snapshot으로 공개한다.")
    @Test
    void publishesEmptySnapshot() throws Exception {
        // arrange
        RankingPeriod period = RankingPeriod.MONTHLY;
        ProductRankingSnapshotHeader snapshot = insertSnapshot(period);

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        ProductRankingSnapshotHeader completed =
            snapshotRepository.findBy(snapshot.key()).orElseThrow();
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(resultRepository.findPublishedRankings(
                period,
                snapshot.id(),
                101
            )).isEmpty(),
            () -> assertThat(completed.completedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    @DisplayName("이미 완료된 Snapshot은 원천과 탈락 후보가 없어도 공개 순위만 검증하고 종료한다.")
    @Test
    void reentersCompletedSnapshotIdempotently() throws Exception {
        // arrange
        RankingPeriod period = RankingPeriod.WEEKLY;
        ProductRankingSnapshotHeader snapshot = insertSnapshot(period);
        List<RankingCandidate> candidates = List.of(
            new RankingCandidate(snapshot.id(), 101L, 5.3),
            new RankingCandidate(snapshot.id(), 202L, 3.0)
        );
        List<ProductRankingAssignment> assignments = List.of(
            new ProductRankingAssignment(101L, 5.3, 1),
            new ProductRankingAssignment(202L, 3.0, 2)
        );
        candidateRepository.upsertAll(candidates);
        resultRepository.insertAll(period, snapshot.id(), assignments);
        Instant previousCompletedAt = Instant.parse("2026-07-20T02:05:00Z");
        snapshotRepository.completeIfIncomplete(snapshot.id(), previousCompletedAt);

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        ProductRankingSnapshotHeader completed =
            snapshotRepository.findBy(snapshot.key()).orElseThrow();
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(resultRepository.findPublishedRankings(
                period,
                snapshot.id(),
                101
            )).containsExactlyElementsOf(assignments),
            () -> assertThat(completed.completedAt()).isEqualTo(previousCompletedAt)
        );
    }

    private ProductRankingSnapshotHeader insertSnapshot(RankingPeriod period) {
        ProductRankingSnapshotKey key = new ProductRankingSnapshotKey(
            period,
            LocalDate.of(2026, 7, 19),
            1
        );
        snapshotRepository.insert(new NewProductRankingSnapshot(
            key,
            SCORE_POLICY,
            CREATED_AT
        ));
        return snapshotRepository.findBy(key).orElseThrow();
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

    private JobParameters jobParameters(RankingPeriod period) {
        return new JobParametersBuilder()
            .addString("period", period.name())
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + tableName,
            Long.class
        );
    }

    private String otherTableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_monthly";
            case MONTHLY -> "mv_product_rank_weekly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJobConfiguration {

        @Bean(TEST_JOB_NAME)
        Job publishProductRankingStepTestJob(
            JobRepository jobRepository,
            @Qualifier(ProductRankingSnapshotJobConfig.PUBLISH_STEP_NAME) Step publishStep
        ) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository)
                .start(publishStep)
                .build();
        }
    }
}
