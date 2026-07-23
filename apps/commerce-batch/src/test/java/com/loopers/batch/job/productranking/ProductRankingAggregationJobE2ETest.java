package com.loopers.batch.job.productranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.CommerceBatchApplication;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBatchTest
@SpringBootTest(classes = CommerceBatchApplication.class)
@TestPropertySource(
    properties = {
      "spring.batch.job.enabled=false",
      "spring.batch.job.name=" + ProductRankingAggregationJobConfig.JOB_NAME
    })
class ProductRankingAggregationJobE2ETest {

  private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 22);

  private final JobLauncherTestUtils jobLauncherTestUtils;
  private final JobRepositoryTestUtils jobRepositoryTestUtils;
  private final JdbcTemplate jdbcTemplate;
  private final Job job;

  @Autowired
  ProductRankingAggregationJobE2ETest(
      JobLauncherTestUtils jobLauncherTestUtils,
      JobRepositoryTestUtils jobRepositoryTestUtils,
      JdbcTemplate jdbcTemplate,
      @Qualifier(ProductRankingAggregationJobConfig.JOB_NAME) Job job) {
    this.jobLauncherTestUtils = jobLauncherTestUtils;
    this.jobRepositoryTestUtils = jobRepositoryTestUtils;
    this.jdbcTemplate = jdbcTemplate;
    this.job = job;
  }

  @BeforeEach
  void setUp() {
    jobLauncherTestUtils.setJob(job);
    jobRepositoryTestUtils.removeJobExecutions();
    recreateBusinessTables();
  }

  @DisplayName("chunk 크기를 넘는 원천을 전역 TOP 100으로 집계하고 기간 경계와 동점을 적용한다.")
  @Test
  void aggregatesGlobalTopOneHundredAcrossPeriodBoundaries() throws Exception {
    insertRankingCandidates();

    JobExecution execution = jobLauncherTestUtils.launchJob(parameters(null));

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(count("mv_product_rank_weekly")).isEqualTo(100);
    assertThat(count("mv_product_rank_monthly")).isEqualTo(100);

    assertThat(snapshot("mv_product_rank_weekly", 1))
        .isEqualTo(new Snapshot(1, 122L, new BigDecimal("1500.0")));
    assertThat(snapshot("mv_product_rank_weekly", 2))
        .isEqualTo(new Snapshot(2, 124L, new BigDecimal("600.0")));
    assertThat(snapshot("mv_product_rank_weekly", 3))
        .isEqualTo(new Snapshot(3, 1L, new BigDecimal("200.0")));
    assertThat(snapshot("mv_product_rank_weekly", 4))
        .isEqualTo(new Snapshot(4, 2L, new BigDecimal("200.0")));
    assertThat(productIds("mv_product_rank_weekly")).doesNotContain(121L, 123L);

    assertThat(snapshot("mv_product_rank_monthly", 1))
        .isEqualTo(new Snapshot(1, 122L, new BigDecimal("1500.0")));
    assertThat(snapshot("mv_product_rank_monthly", 2))
        .isEqualTo(new Snapshot(2, 124L, new BigDecimal("1200.0")));
    assertThat(snapshot("mv_product_rank_monthly", 3))
        .isEqualTo(new Snapshot(3, 121L, new BigDecimal("1000.0")));
    assertThat(snapshot("mv_product_rank_monthly", 4))
        .isEqualTo(new Snapshot(4, 1L, new BigDecimal("200.0")));
    assertThat(snapshot("mv_product_rank_monthly", 5))
        .isEqualTo(new Snapshot(5, 2L, new BigDecimal("200.0")));
    assertThat(productIds("mv_product_rank_monthly")).doesNotContain(123L);

    assertThat(periodStart("mv_product_rank_weekly")).isEqualTo(LocalDate.of(2026, 7, 20));
    assertThat(periodStart("mv_product_rank_monthly")).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(count("stg_product_rank_aggregation")).isZero();
  }

  @DisplayName("게시 실패는 기존 MV를 보존하고 같은 JobInstance 재시작은 점수를 중복 누적하지 않는다.")
  @Test
  void preservesSnapshotOnFailureAndRestartsSameJobInstance() throws Exception {
    insertMetric(TARGET_DATE, 1L, 10L, 5L, 2L);
    insertSnapshot("mv_product_rank_weekly", 999L, new BigDecimal("999.0"));
    jdbcTemplate.execute(
        """
        CREATE TRIGGER fail_weekly_snapshot_insert
        BEFORE INSERT ON mv_product_rank_weekly
        FOR EACH ROW
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced publish failure'
        """);

    JobParameters parameters = parameters(null);
    JobExecution failedExecution = jobLauncherTestUtils.launchJob(parameters);

    assertThat(failedExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(snapshot("mv_product_rank_weekly", 1).productId()).isEqualTo(999L);
    assertThat(stagingScore(failedExecution.getJobInstance().getInstanceId(), "WEEKLY", 1L))
        .isEqualByComparingTo("4.0");

    jdbcTemplate.execute("DROP TRIGGER fail_weekly_snapshot_insert");
    JobExecution restartedExecution = jobLauncherTestUtils.launchJob(parameters);

    assertThat(restartedExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restartedExecution.getJobInstance().getInstanceId())
        .isEqualTo(failedExecution.getJobInstance().getInstanceId());
    assertThat(snapshot("mv_product_rank_weekly", 1))
        .isEqualTo(new Snapshot(1, 1L, new BigDecimal("4.0")));
    assertThat(count("stg_product_rank_aggregation")).isZero();

    jdbcTemplate.update(
        """
        UPDATE product_metrics
        SET view_count = 20, like_count = 5, order_count = 2
        WHERE metric_date = ? AND product_id = ?
        """,
        Date.valueOf(TARGET_DATE),
        1L);
    JobExecution rebuildExecution = jobLauncherTestUtils.launchJob(parameters("1"));

    assertThat(rebuildExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(rebuildExecution.getJobInstance().getInstanceId())
        .isNotEqualTo(restartedExecution.getJobInstance().getInstanceId());
    assertThat(snapshot("mv_product_rank_weekly", 1))
        .isEqualTo(new Snapshot(1, 1L, new BigDecimal("5.0")));
  }

  @DisplayName("두 번째 chunk 실패 후 재시작하면 저장된 paging 상태부터 이어서 점수를 한 번만 누적한다.")
  @Test
  void resumesFromLastCommittedChunkWithoutDuplicatingScores() throws Exception {
    for (long productId = 1; productId <= 120; productId++) {
      insertMetric(TARGET_DATE, productId, 0L, 0L, 1L);
    }
    insertSnapshot("mv_product_rank_weekly", 999L, new BigDecimal("999.0"));
    jdbcTemplate.execute(
        """
        CREATE TRIGGER fail_second_chunk_insert
        BEFORE INSERT ON stg_product_rank_aggregation
        FOR EACH ROW
        BEGIN
          IF NEW.period_type = 'WEEKLY' AND NEW.product_id = 101 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced second chunk failure';
          END IF;
        END
        """);

    JobParameters parameters = parameters(null);
    JobExecution failedExecution = jobLauncherTestUtils.launchJob(parameters);

    assertThat(failedExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(snapshot("mv_product_rank_weekly", 1).productId()).isEqualTo(999L);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stg_product_rank_aggregation
                WHERE job_instance_id = ? AND period_type = 'WEEKLY'
                """,
                Integer.class,
                failedExecution.getJobInstance().getInstanceId()))
        .isEqualTo(100);

    jdbcTemplate.execute("DROP TRIGGER fail_second_chunk_insert");
    JobExecution restartedExecution = jobLauncherTestUtils.launchJob(parameters);

    assertThat(restartedExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restartedExecution.getJobInstance().getInstanceId())
        .isEqualTo(failedExecution.getJobInstance().getInstanceId());
    assertThat(snapshot("mv_product_rank_weekly", 1))
        .isEqualTo(new Snapshot(1, 1L, new BigDecimal("1.0")));
    assertThat(snapshot("mv_product_rank_weekly", 100))
        .isEqualTo(new Snapshot(100, 100L, new BigDecimal("1.0")));
  }

  @DisplayName("원천이 비어 있으면 해당 날짜의 기존 주간·월간 스냅샷을 빈 결과로 교체한다.")
  @Test
  void publishesEmptySnapshots() throws Exception {
    insertSnapshot("mv_product_rank_weekly", 1L, BigDecimal.TEN);
    insertSnapshot("mv_product_rank_monthly", 1L, BigDecimal.TEN);

    JobExecution execution = jobLauncherTestUtils.launchJob(parameters(null));

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(count("mv_product_rank_weekly")).isZero();
    assertThat(count("mv_product_rank_monthly")).isZero();
  }

  private void insertRankingCandidates() {
    List<Object[]> rows = new ArrayList<>();
    LocalDate weeklyStart = LocalDate.of(2026, 7, 20);
    for (long productId = 1; productId <= 120; productId++) {
      long orderCount = productId <= 2 ? 200 : 200 - productId;
      rows.add(new Object[] {Date.valueOf(weeklyStart), productId, 0L, 0L, orderCount});
    }
    rows.add(new Object[] {Date.valueOf(TARGET_DATE), 122L, 0L, 0L, 1500L});
    rows.add(new Object[] {Date.valueOf(weeklyStart.minusDays(1)), 121L, 0L, 0L, 1000L});
    rows.add(new Object[] {Date.valueOf(weeklyStart.minusDays(1)), 124L, 0L, 0L, 600L});
    rows.add(new Object[] {Date.valueOf(TARGET_DATE), 124L, 0L, 0L, 600L});
    rows.add(new Object[] {Date.valueOf(LocalDate.of(2026, 6, 30)), 123L, 0L, 0L, 5000L});

    jdbcTemplate.batchUpdate(
        """
        INSERT INTO product_metrics (
            metric_date, product_id, view_count, like_count, order_count
        ) VALUES (?, ?, ?, ?, ?)
        """,
        rows);
  }

  private void insertMetric(
      LocalDate metricDate, long productId, long viewCount, long likeCount, long orderCount) {
    jdbcTemplate.update(
        """
        INSERT INTO product_metrics (
            metric_date, product_id, view_count, like_count, order_count
        ) VALUES (?, ?, ?, ?, ?)
        """,
        Date.valueOf(metricDate),
        productId,
        viewCount,
        likeCount,
        orderCount);
  }

  private void insertSnapshot(String table, long productId, BigDecimal score) {
    jdbcTemplate.update(
        "INSERT INTO "
            + table
            + " (aggregation_date, period_start_date, period_end_date,"
            + " rank_position, product_id, score, generated_at)"
            + " VALUES (?, ?, ?, 1, ?, ?, CURRENT_TIMESTAMP(6))",
        Date.valueOf(TARGET_DATE),
        Date.valueOf(TARGET_DATE),
        Date.valueOf(TARGET_DATE),
        productId,
        score);
  }

  private Snapshot snapshot(String table, int rank) {
    return jdbcTemplate.queryForObject(
        "SELECT rank_position, product_id, score FROM "
            + table
            + " WHERE aggregation_date = ? AND rank_position = ?",
        (resultSet, rowNumber) ->
            new Snapshot(
                resultSet.getInt("rank_position"),
                resultSet.getLong("product_id"),
                resultSet.getBigDecimal("score")),
        Date.valueOf(TARGET_DATE),
        rank);
  }

  private List<Long> productIds(String table) {
    return jdbcTemplate.queryForList(
        "SELECT product_id FROM " + table + " WHERE aggregation_date = ?",
        Long.class,
        Date.valueOf(TARGET_DATE));
  }

  private LocalDate periodStart(String table) {
    return jdbcTemplate
        .queryForObject(
            "SELECT period_start_date FROM "
                + table
                + " WHERE aggregation_date = ? AND rank_position = 1",
            Date.class,
            Date.valueOf(TARGET_DATE))
        .toLocalDate();
  }

  private BigDecimal stagingScore(long jobInstanceId, String periodType, long productId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT score
        FROM stg_product_rank_aggregation
        WHERE job_instance_id = ? AND period_type = ? AND product_id = ?
        """,
        BigDecimal.class,
        jobInstanceId,
        periodType,
        productId);
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  private JobParameters parameters(String rebuildSequence) {
    JobParametersBuilder builder =
        new JobParametersBuilder()
            .addString(ProductRankingJobParametersValidator.TARGET_DATE, "20260722");
    if (rebuildSequence != null) {
      builder.addString(ProductRankingJobParametersValidator.REBUILD_SEQUENCE, rebuildSequence);
    }
    return builder.toJobParameters();
  }

  private void recreateBusinessTables() {
    jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_weekly_snapshot_insert");
    jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_second_chunk_insert");
    jdbcTemplate.execute("DROP TABLE IF EXISTS stg_product_rank_aggregation");
    jdbcTemplate.execute("DROP TABLE IF EXISTS mv_product_rank_monthly");
    jdbcTemplate.execute("DROP TABLE IF EXISTS mv_product_rank_weekly");
    jdbcTemplate.execute("DROP TABLE IF EXISTS product_metrics");
    jdbcTemplate.execute(
        """
        CREATE TABLE product_metrics (
            metric_date DATE NOT NULL,
            product_id BIGINT NOT NULL,
            view_count BIGINT NOT NULL,
            like_count BIGINT NOT NULL,
            order_count BIGINT NOT NULL,
            PRIMARY KEY (metric_date, product_id)
        ) ENGINE=InnoDB
        """);
    createSnapshotTable("mv_product_rank_weekly");
    createSnapshotTable("mv_product_rank_monthly");
    jdbcTemplate.execute(
        """
        CREATE TABLE stg_product_rank_aggregation (
            job_instance_id BIGINT NOT NULL,
            period_type VARCHAR(10) NOT NULL,
            product_id BIGINT NOT NULL,
            score DECIMAL(30, 1) NOT NULL,
            created_at DATETIME(6) NOT NULL,
            updated_at DATETIME(6) NOT NULL,
            PRIMARY KEY (job_instance_id, period_type, product_id)
        ) ENGINE=InnoDB
        """);
  }

  private void createSnapshotTable(String table) {
    jdbcTemplate.execute(
        """
        CREATE TABLE %s (
            aggregation_date DATE NOT NULL,
            period_start_date DATE NOT NULL,
            period_end_date DATE NOT NULL,
            rank_position SMALLINT UNSIGNED NOT NULL,
            product_id BIGINT NOT NULL,
            score DECIMAL(30, 1) NOT NULL,
            generated_at DATETIME(6) NOT NULL,
            PRIMARY KEY (aggregation_date, rank_position),
            UNIQUE (aggregation_date, product_id)
        ) ENGINE=InnoDB
        """
            .formatted(table));
  }

  private record Snapshot(int rank, long productId, BigDecimal score) {}
}
