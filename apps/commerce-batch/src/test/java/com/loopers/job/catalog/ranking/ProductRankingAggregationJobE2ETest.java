package com.loopers.job.catalog.ranking;

import org.junit.jupiter.api.BeforeEach;
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

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=productRankingAggregationJob")
class ProductRankingAggregationJobE2ETest {

    private static final AtomicLong RUN_ID = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("productRankingAggregationJob")
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from mv_product_rank_weekly");
        jdbcTemplate.update("delete from mv_product_rank_monthly");
        jdbcTemplate.update("delete from product_metrics");
    }

    @DisplayName("period 또는 baseDate JobParameter가 없으면 랭킹 집계 Job은 실패한다.")
    @Test
    void failsWithoutRequiredJobParameters() throws Exception {
        // arrange
        jobLauncherTestUtils.setJob(job);

        // act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // assert
        assertAll(
            () -> assertThat(jobExecution).isNotNull(),
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode())
        );
    }

    @DisplayName("baseDate가 속한 주의 product_metrics를 합산해 주간 랭킹 MV에 점수순으로 저장한다.")
    @Test
    void aggregatesWeeklyRankingByPeriodAndScoreFormula() throws Exception {
        // arrange
        jobLauncherTestUtils.setJob(job);
        LocalDate monday = LocalDate.of(2026, 7, 20);
        insertMetric(monday, 1L, 10L, 5L, 100L);
        insertMetric(monday.plusDays(6), 1L, 0L, 0L, 50L);
        insertMetric(monday.plusDays(2), 2L, 500L, 0L, 0L);
        insertMetric(monday.minusDays(1), 3L, 0L, 0L, 1_000L);

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("period", "weekly")
            .addString("baseDate", "20260722")
            .addLong("run.id", nextRunId())
            .toJobParameters());

        // assert
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select product_id, `rank`, score
            from mv_product_rank_weekly
            where period_start_date = ? and period_end_date = ?
            order by `rank` asc
            """, Date.valueOf(monday), Date.valueOf(monday.plusDays(6)));

        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rows).hasSize(2),
            () -> assertThat(rows.get(0).get("product_id")).isEqualTo(1L),
            () -> assertThat(rows.get(0).get("rank")).isEqualTo(1L),
            () -> assertThat(((Number) rows.get(0).get("score")).doubleValue()).isEqualTo(92.0),
            () -> assertThat(rows.get(1).get("product_id")).isEqualTo(2L),
            () -> assertThat(rows.get(1).get("rank")).isEqualTo(2L),
            () -> assertThat(((Number) rows.get(1).get("score")).doubleValue()).isEqualTo(50.0)
        );
    }

    @DisplayName("같은 월 기간 결과는 삭제 후 재적재하고 TOP 100까지만 월간 랭킹 MV에 저장한다.")
    @Test
    void replacesExistingMonthlyRowsAndKeepsTop100() throws Exception {
        // arrange
        jobLauncherTestUtils.setJob(job);
        LocalDate monthStart = LocalDate.of(2026, 7, 1);
        LocalDate monthEnd = LocalDate.of(2026, 7, 31);
        insertMonthlyRank(monthStart, monthEnd, 1L, 9999L, 9999.0);
        for (long productId = 1L; productId <= 105L; productId++) {
            insertMetric(monthStart.plusDays((int) (productId % 31)), productId, productId, 0L, 0L);
        }

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("period", "monthly")
            .addString("baseDate", "20260715")
            .addLong("run.id", nextRunId())
            .toJobParameters());

        // assert
        Long rowCount = jdbcTemplate.queryForObject("""
            select count(*)
            from mv_product_rank_monthly
            where period_start_date = ? and period_end_date = ?
            """, Long.class, Date.valueOf(monthStart), Date.valueOf(monthEnd));
        List<Map<String, Object>> firstAndLastRows = jdbcTemplate.queryForList("""
            select product_id, `rank`, score
            from mv_product_rank_monthly
            where period_start_date = ? and period_end_date = ? and `rank` in (1, 100)
            order by `rank` asc
            """, Date.valueOf(monthStart), Date.valueOf(monthEnd));
        Long staleRowCount = jdbcTemplate.queryForObject("""
            select count(*)
            from mv_product_rank_monthly
            where period_start_date = ? and period_end_date = ? and product_id = 9999
            """, Long.class, Date.valueOf(monthStart), Date.valueOf(monthEnd));

        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rowCount).isEqualTo(100L),
            () -> assertThat(staleRowCount).isZero(),
            () -> assertThat(firstAndLastRows.get(0).get("product_id")).isEqualTo(105L),
            () -> assertThat(firstAndLastRows.get(0).get("rank")).isEqualTo(1L),
            () -> assertThat(firstAndLastRows.get(1).get("product_id")).isEqualTo(6L),
            () -> assertThat(firstAndLastRows.get(1).get("rank")).isEqualTo(100L)
        );
    }

    private void insertMetric(LocalDate metricDate, Long productId, Long viewCount, Long likeCount, Long salesAmount) {
        jdbcTemplate.update("""
            insert into product_metrics (
                metric_date, product_id, like_count, sales_count, sales_amount, view_count,
                created_at, updated_at
            ) values (?, ?, ?, 0, ?, ?, now(), now())
            """,
            Date.valueOf(metricDate),
            productId,
            likeCount,
            salesAmount,
            viewCount
        );
    }

    private long nextRunId() {
        return RUN_ID.incrementAndGet();
    }

    private void insertMonthlyRank(
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        Long rank,
        Long productId,
        Double score
    ) {
        jdbcTemplate.update("""
            insert into mv_product_rank_monthly (
                period_start_date, period_end_date, `rank`, product_id, score,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, now(), now())
            """,
            Date.valueOf(periodStartDate),
            Date.valueOf(periodEndDate),
            rank,
            productId,
            score
        );
    }
}
