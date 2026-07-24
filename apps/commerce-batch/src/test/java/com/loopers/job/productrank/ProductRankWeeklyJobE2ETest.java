package com.loopers.job.productrank;

import com.loopers.batch.job.productrank.ProductRankWeeklyJobConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + ProductRankWeeklyJobConfig.JOB_NAME)
@Import(MySqlTestContainersConfig.class)
@DisplayName("ProductRankWeeklyJob E2E 테스트")
class ProductRankWeeklyJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ProductRankWeeklyJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private JobRepository jobRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("[ECP] 최근 7일(어제 기준) product_metric_daily를 가중합해 mv_product_rank_weekly에 적재한다.")
    @Test
    void aggregatesLast7Days_intoWeeklyMv() throws Exception {
        // arrange
        LocalDate requestDate = LocalDate.of(2026, 7, 23);
        LocalDate yesterday = requestDate.minusDays(1); // 2026-07-22, 윈도우 [07-16, 07-22]
        LocalDate outOfWindow = yesterday.minusDays(7); // 2026-07-15, 윈도우 밖

        insertDailyMetric("PRD_01", yesterday, 10, 5, 2);
        insertDailyMetric("PRD_01", yesterday.minusDays(6), 3, 1, 0); // 윈도우 경계 포함(07-16)
        insertDailyMetric("PRD_01", outOfWindow, 100, 100, 100); // 윈도우 밖, 집계 제외
        insertDailyMetric("PRD_01", requestDate, 999, 999, 999); // 오늘, 집계 제외 (Q&A #5)
        insertDailyMetric("PRD_02", yesterday, 1, 1, 1);

        jobLauncherTestUtils.setJob(job);
        var jobParameters = new JobParametersBuilder()
                .addString("requestDate", requestDate.toString())
                .toJobParameters();

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        var prd01 = jdbcTemplate.queryForMap(
                "SELECT score, view_sum, like_delta_sum, purchase_quantity_sum FROM mv_product_rank_weekly WHERE as_of_date = ? AND product_id = ?",
                requestDate, "PRD_01");
        assertAll(
                () -> assertThat(((Number) prd01.get("view_sum")).longValue()).isEqualTo(13L),
                () -> assertThat(((Number) prd01.get("like_delta_sum")).longValue()).isEqualTo(6L),
                () -> assertThat(((Number) prd01.get("purchase_quantity_sum")).longValue()).isEqualTo(2L),
                () -> assertThat(((Number) prd01.get("score")).doubleValue())
                        .isCloseTo(13 * 0.1 + 6 * 0.2 + 2 * 0.7, offset(1e-9))
        );

        Long prd02Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE as_of_date = ? AND product_id = ?",
                Long.class, requestDate, "PRD_02");
        assertThat(prd02Count).isEqualTo(1L);
    }

    @DisplayName("[ECP] 동일 requestDate로 재실행해도 score가 중복 합산되지 않는다 (cleanup 후 재집계).")
    @Test
    void doesNotDoubleCount_whenRerunWithSameRequestDate() throws Exception {
        // arrange
        LocalDate requestDate = LocalDate.of(2026, 7, 23);
        LocalDate yesterday = requestDate.minusDays(1);
        insertDailyMetric("PRD_01", yesterday, 10, 5, 2);

        jobLauncherTestUtils.setJob(job);
        var jobParameters1 = new JobParametersBuilder()
                .addString("requestDate", requestDate.toString())
                .addLong("run", 1L)
                .toJobParameters();
        var jobParameters2 = new JobParametersBuilder()
                .addString("requestDate", requestDate.toString())
                .addLong("run", 2L)
                .toJobParameters();

        // act
        jobLauncherTestUtils.launchJob(jobParameters1);
        jobLauncherTestUtils.launchJob(jobParameters2);

        // assert
        Long viewSum = jdbcTemplate.queryForObject(
                "SELECT view_sum FROM mv_product_rank_weekly WHERE as_of_date = ? AND product_id = ?",
                Long.class, requestDate, "PRD_01");
        assertThat(viewSum).isEqualTo(10L);
    }

    @DisplayName("[Error Guessing] cleanup 완료 후 aggregate 단계에서 실패한 뒤 같은 requestDate로 재시작하면, cleanup이 다시 실행되고 전체가 처음부터 재집계된다.")
    @Test
    void rerunsCleanupAndFullyRecomputes_whenRestartedAfterAggregateStepFailure() throws Exception {
        // arrange
        // 다른 테스트와 같은 requestDate를 쓰면 Spring Batch 메타데이터(BATCH_JOB_INSTANCE 등)가
        // DatabaseCleanUp 대상이 아니라서(JPA 엔티티가 아님) JobInstance가 충돌한다 — 이 테스트만의 날짜를 쓴다.
        LocalDate requestDate = LocalDate.of(2026, 7, 30);
        LocalDate yesterday = requestDate.minusDays(1);
        insertDailyMetric("PRD_01", yesterday, 10, 5, 2);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestDate", requestDate.toString())
                .toJobParameters();

        // 이전 시도에서 aggregate 단계가 실패하기 전, cleanup 단계는 이미 COMPLETED로
        // 기록되고 그 시점까지 write된 "부분 결과"가 남아있는 상황을 재현한다.
        insertWeeklyMvRow(requestDate, "PRD_LEFTOVER", 999.0);
        JobExecution failedExecution = jobRepository.createJobExecution(ProductRankWeeklyJobConfig.JOB_NAME, jobParameters);
        StepExecution cleanupStepExecution = new StepExecution("productRankWeeklyCleanupStep", failedExecution);
        jobRepository.add(cleanupStepExecution);
        cleanupStepExecution.setStatus(BatchStatus.COMPLETED);
        cleanupStepExecution.setExitStatus(ExitStatus.COMPLETED);
        jobRepository.update(cleanupStepExecution);
        failedExecution.setStatus(BatchStatus.FAILED);
        failedExecution.setExitStatus(ExitStatus.FAILED);
        jobRepository.update(failedExecution);

        jobLauncherTestUtils.setJob(job);

        // act — 같은 JobParameters로 재실행하면 Spring Batch는 기존 JobInstance의 재시작으로 처리한다.
        JobExecution restartExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        assertThat(restartExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        Long leftoverCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE as_of_date = ? AND product_id = ?",
                Long.class, requestDate, "PRD_LEFTOVER");
        assertThat(leftoverCount).isEqualTo(0L);

        Long viewSum = jdbcTemplate.queryForObject(
                "SELECT view_sum FROM mv_product_rank_weekly WHERE as_of_date = ? AND product_id = ?",
                Long.class, requestDate, "PRD_01");
        assertThat(viewSum).isEqualTo(10L);
    }

    private void insertWeeklyMvRow(LocalDate asOfDate, String productId, double score) {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly (as_of_date, product_id, score, view_sum, like_delta_sum, purchase_quantity_sum, created_at)
                VALUES (?, ?, ?, 0, 0, 0, ?)
                """, asOfDate, productId, score, ZonedDateTime.now());
    }

    private void insertDailyMetric(String productId, LocalDate metricDate, long viewCount, long likeDeltaCount, long purchaseQuantity) {
        jdbcTemplate.update("""
                INSERT INTO product_metric_daily (metric_date, product_id, view_count, like_delta_count, purchase_quantity, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, metricDate, productId, viewCount, likeDeltaCount, purchaseQuantity, ZonedDateTime.now(), ZonedDateTime.now());
    }
}
