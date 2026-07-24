package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingAssignment;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.ProductRankingResultRepository;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
class ProductRankingSnapshotJobE2ETest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-20T02:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-20T02:10:00.123456Z");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(ProductRankingSnapshotJobConfig.JOB_NAME)
    private Job job;

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
        jobLauncherTestUtils.setJob(job);
        when(clock.instant()).thenReturn(CREATED_AT, COMPLETED_AT);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("고정 일간 Metric을 준비, 점수 계산, 공개 순서로 주간·월간 랭킹으로 만든다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void createsAndPublishesProductRankingSnapshot(RankingPeriod period) throws Exception {
        // arrange
        insertFixedMetrics();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        ProductRankingSnapshotKey key = snapshotKey(period);
        ProductRankingSnapshotHeader snapshot = snapshotRepository.findBy(key).orElseThrow();
        List<ProductRankingAssignment> rankings =
            resultRepository.findPublishedRankings(period, snapshot.id(), 101);
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly(
                    ProductRankingSnapshotJobConfig.PREPARE_STEP_NAME,
                    ProductRankingSnapshotJobConfig.CALCULATE_STEP_NAME,
                    ProductRankingSnapshotJobConfig.PUBLISH_STEP_NAME
                ),
            () -> assertThat(rankings).containsExactlyElementsOf(expectedRankings(period)),
            () -> assertThat(candidateRepository.countCandidates(snapshot.id()))
                .isEqualTo(expectedRankings(period).size()),
            () -> assertThat(snapshot.createdAt()).isEqualTo(CREATED_AT),
            () -> assertThat(snapshot.completedAt()).isEqualTo(COMPLETED_AT),
            () -> assertThat(execution.getExecutionContext().containsKey("startTime")).isTrue()
        );
    }

    @DisplayName("대상 Metric이 없어도 세 단계를 마치고 완료된 Empty Snapshot을 남긴다.")
    @Test
    void publishesEmptySnapshot() throws Exception {
        // arrange
        RankingPeriod period = RankingPeriod.WEEKLY;

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(period));

        // assert
        ProductRankingSnapshotHeader snapshot =
            snapshotRepository.findBy(snapshotKey(period)).orElseThrow();
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(execution.getStepExecutions()).hasSize(3),
            () -> assertThat(resultRepository.findPublishedRankings(
                period,
                snapshot.id(),
                101
            )).isEmpty(),
            () -> assertThat(snapshot.completedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    @DisplayName("run.id를 추가한 실행은 Snapshot을 만들기 전에 Job Validator가 거절한다.")
    @Test
    void rejectsRunIdParameter() {
        // arrange
        JobParameters invalidParameters = new JobParametersBuilder(jobParameters(
            RankingPeriod.WEEKLY
        ))
            .addLong("run.id", 1L)
            .toJobParameters();

        // act & assert
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(invalidParameters))
            .isInstanceOf(JobParametersInvalidException.class)
            .hasMessageContaining("identifying parameters");
        assertThat(snapshotRepository.findBy(snapshotKey(RankingPeriod.WEEKLY))).isEmpty();
    }

    @DisplayName("성공한 같은 실행 키는 반복하지 않고 revision 증가만 새 재집계로 실행한다.")
    @Test
    void createsNewJobInstanceOnlyForNextRevision() throws Exception {
        // arrange
        RankingPeriod period = RankingPeriod.WEEKLY;
        JobParameters firstRevisionParameters = jobParameters(period);

        // act
        JobExecution firstExecution =
            jobLauncherTestUtils.launchJob(firstRevisionParameters);

        // assert
        assertThat(firstExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(firstRevisionParameters))
            .isInstanceOf(JobInstanceAlreadyCompleteException.class);

        // act
        JobParameters nextRevisionParameters = jobParameters(period, 2);
        JobExecution nextRevisionExecution =
            jobLauncherTestUtils.launchJob(nextRevisionParameters);

        // assert
        ProductRankingSnapshotHeader firstSnapshot =
            snapshotRepository.findBy(snapshotKey(period)).orElseThrow();
        ProductRankingSnapshotHeader nextSnapshot =
            snapshotRepository.findBy(snapshotKey(period, 2)).orElseThrow();
        assertAll(
            () -> assertThat(nextRevisionExecution.getExitStatus())
                .isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(nextRevisionExecution.getJobInstance().getInstanceId())
                .isNotEqualTo(firstExecution.getJobInstance().getInstanceId()),
            () -> assertThat(nextSnapshot.id()).isNotEqualTo(firstSnapshot.id()),
            () -> assertThat(nextSnapshot.completedAt()).isNotNull()
        );
    }

    private List<ProductRankingAssignment> expectedRankings(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> List.of(
                new ProductRankingAssignment(101L, 5.3, 1),
                new ProductRankingAssignment(202L, 3.0, 2)
            );
            case MONTHLY -> List.of(
                new ProductRankingAssignment(303L, 10.0, 1),
                new ProductRankingAssignment(101L, 5.3, 2),
                new ProductRankingAssignment(202L, 3.0, 3)
            );
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }

    private void insertFixedMetrics() {
        insertMetric(LocalDate.of(2026, 7, 13), 101L, 10, 2, 10_000);
        insertMetric(LocalDate.of(2026, 7, 19), 101L, 20, -1, 20_000);
        insertMetric(LocalDate.of(2026, 7, 15), 202L, 30, 0, 0);
        insertMetric(LocalDate.of(2026, 7, 1), 303L, 100, 0, 0);
        insertMetric(LocalDate.of(2026, 6, 30), 404L, 999, 0, 0);
        insertMetric(LocalDate.of(2026, 7, 20), 505L, 999, 0, 0);
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

    private ProductRankingSnapshotKey snapshotKey(RankingPeriod period) {
        return snapshotKey(period, 1);
    }

    private ProductRankingSnapshotKey snapshotKey(RankingPeriod period, int revision) {
        return new ProductRankingSnapshotKey(
            period,
            LocalDate.of(2026, 7, 19),
            revision
        );
    }

    private JobParameters jobParameters(RankingPeriod period) {
        return jobParameters(period, 1);
    }

    private JobParameters jobParameters(RankingPeriod period, long revision) {
        return new JobParametersBuilder()
            .addString("period", period.name())
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", revision)
            .toJobParameters();
    }
}
