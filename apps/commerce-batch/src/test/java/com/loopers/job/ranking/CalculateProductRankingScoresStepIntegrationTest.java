package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false",
    "commerce.ranking.batch.chunk-size=2"
})
@SpringBatchTest
@Import(CalculateProductRankingScoresStepIntegrationTest.TestJobConfiguration.class)
class CalculateProductRankingScoresStepIntegrationTest {

    private static final String TEST_JOB_NAME = "calculateProductRankingScoresStepTestJob";
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(testJob);
        insertMetric(LocalDate.of(2026, 7, 13), 101L, 10, 2, 10_000);
        insertMetric(LocalDate.of(2026, 7, 19), 101L, 20, -1, 20_000);
        insertMetric(LocalDate.of(2026, 7, 15), 202L, 30, 0, 0);
        insertMetric(LocalDate.of(2026, 7, 1), 303L, 100, 0, 0);
        insertMetric(LocalDate.of(2026, 6, 30), 404L, 999, 0, 0);
        insertMetric(LocalDate.of(2026, 7, 20), 505L, 999, 0, 0);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("기간 내 상품별 점수를 계산해 공개 MV와 분리된 후보로 저장한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void calculatesAndStoresCandidates(RankingPeriod period) throws Exception {
        // arrange
        ProductRankingSnapshotKey key = snapshotKey(period);
        snapshotRepository.insert(
            new NewProductRankingSnapshot(
                key,
                SCORE_POLICY,
                Instant.parse("2026-07-20T02:00:00Z")
            )
        );

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        ProductRankingSnapshotHeader snapshot = snapshotRepository.findBy(key).orElseThrow();
        List<CandidateRow> candidates = candidates(snapshot.id());
        int expectedCount = period == RankingPeriod.WEEKLY ? 2 : 3;
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(stepExecution.getStepName())
                .isEqualTo(ProductRankingSnapshotJobConfig.CALCULATE_STEP_NAME),
            () -> assertThat(stepExecution.getReadCount()).isEqualTo(expectedCount),
            () -> assertThat(stepExecution.getWriteCount()).isEqualTo(expectedCount),
            () -> assertThat(stepExecution.getCommitCount()).isEqualTo(2),
            () -> assertThat(stepExecution.getSkipCount()).isZero(),
            () -> assertThat(candidates).hasSize(expectedCount),
            () -> assertThat(candidates.get(0).productId()).isEqualTo(101L),
            () -> assertThat(candidates.get(0).score()).isCloseTo(5.3, offset(1.0e-10)),
            () -> assertThat(candidates.get(1).productId()).isEqualTo(202L),
            () -> assertThat(candidates.get(1).score()).isCloseTo(3.0, offset(1.0e-10)),
            () -> {
                if (period == RankingPeriod.MONTHLY) {
                    assertThat(candidates.get(2).productId()).isEqualTo(303L);
                    assertThat(candidates.get(2).score()).isCloseTo(10.0, offset(1.0e-10));
                }
            },
            () -> assertThat(snapshot.completedAt()).isNull(),
            () -> assertThat(countCandidates("mv_product_rank_weekly")).isZero(),
            () -> assertThat(countCandidates("mv_product_rank_monthly")).isZero()
        );
    }

    private JobParameters jobParameters(RankingPeriod period) {
        return new JobParametersBuilder()
            .addString("period", period.name())
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();
    }

    private ProductRankingSnapshotKey snapshotKey(RankingPeriod period) {
        return new ProductRankingSnapshotKey(
            period,
            LocalDate.of(2026, 7, 19),
            1
        );
    }

    private List<CandidateRow> candidates(long snapshotId) {
        return jdbcTemplate.query(
            """
                select product_id, score
                from product_rank_candidates
                where snapshot_id = ?
                order by product_id
                """,
            (resultSet, rowNumber) -> new CandidateRow(
                resultSet.getLong("product_id"),
                resultSet.getDouble("score")
            ),
            snapshotId
        );
    }

    private long countCandidates(String tableName) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + tableName,
            Long.class
        );
    }

    private void insertMetric(
        LocalDate metricDate,
        long productId,
        long viewCount,
        long likeDelta,
        long orderAmount
    ) {
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
                values (?, ?, ?, ?, 0, ?, ?)
                """,
            metricDate,
            productId,
            viewCount,
            likeDelta,
            orderAmount,
            LocalDateTime.of(2026, 7, 20, 2, 0)
        );
    }

    private record CandidateRow(long productId, double score) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJobConfiguration {

        @Bean(TEST_JOB_NAME)
        Job calculateProductRankingScoresStepTestJob(
            JobRepository jobRepository,
            @Qualifier(ProductRankingSnapshotJobConfig.CALCULATE_STEP_NAME) Step calculateStep
        ) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository)
                .start(calculateStep)
                .build();
        }
    }
}
