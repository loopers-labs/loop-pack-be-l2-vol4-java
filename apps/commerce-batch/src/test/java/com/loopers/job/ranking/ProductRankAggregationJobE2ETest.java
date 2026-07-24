package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankAggregationJobConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + ProductRankAggregationJobConfig.JOB_NAME)
class ProductRankAggregationJobE2ETest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(ProductRankAggregationJobConfig.JOB_NAME) private Job job;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("weekly job aggregates daily metrics into a top-100 materialized view")
    @Test
    void aggregatesWeeklyRankings() throws Exception {
        LocalDate targetDate = LocalDate.of(2026, 7, 23);
        givenDailyMetric(LocalDate.of(2026, 7, 20), 1L, 10, 0, 0, 0.0);
        givenDailyMetric(LocalDate.of(2026, 7, 21), 1L, 0, 1, 0, 0.0);
        givenDailyMetric(LocalDate.of(2026, 7, 22), 2L, 0, 0, 1, 10.0);
        givenDailyMetric(LocalDate.of(2026, 7, 13), 3L, 999, 999, 999, 999.0);

        jobLauncherTestUtils.setJob(job);
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("period", "weekly")
            .addString("targetDate", "20260723")
            .addLong("run.id", System.nanoTime())
            .toJobParameters());

        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT rank_no, product_id, view_count, like_count, sale_count FROM mv_product_rank_weekly "
                + "WHERE period_start_date = ? ORDER BY rank_no",
            targetDate.with(java.time.DayOfWeek.MONDAY));
        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0).get("rank_no")).intValue()).isEqualTo(1);
        assertThat(((Number) rows.get(0).get("product_id")).longValue()).isEqualTo(2L);
        assertThat(((Number) rows.get(1).get("product_id")).longValue()).isEqualTo(1L);
    }

    @DisplayName("monthly job refreshes only the top 100 rows for the target month")
    @Test
    void aggregatesMonthlyTop100Rankings() throws Exception {
        for (long productId = 1; productId <= 101; productId++) {
            givenDailyMetric(LocalDate.of(2026, 7, 1), productId, productId, 0, 0, 0.0);
        }

        jobLauncherTestUtils.setJob(job);
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("period", "monthly")
            .addString("targetDate", "20260723")
            .addLong("run.id", System.nanoTime())
            .toJobParameters());

        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE period_start_date = ?",
            Integer.class,
            LocalDate.of(2026, 7, 1));
        Long firstProductId = jdbcTemplate.queryForObject(
            "SELECT product_id FROM mv_product_rank_monthly WHERE period_start_date = ? AND rank_no = 1",
            Long.class,
            LocalDate.of(2026, 7, 1));

        assertThat(count).isEqualTo(100);
        assertThat(firstProductId).isEqualTo(101L);
    }

    private void givenDailyMetric(
        LocalDate metricDate,
        Long productId,
        long viewCount,
        long likeCount,
        long saleCount,
        double orderScore
    ) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_daily "
                + "(metric_date, product_id, view_count, like_count, sale_count, order_score, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW(6))",
            metricDate, productId, viewCount, likeCount, saleCount, orderScore);
    }
}
