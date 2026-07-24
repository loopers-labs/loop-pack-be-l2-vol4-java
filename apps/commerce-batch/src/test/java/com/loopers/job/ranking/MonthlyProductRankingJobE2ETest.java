package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.MonthlyProductRankingJobConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyProductRankingJobConfig.JOB_NAME)
class MonthlyProductRankingJobE2ETest {
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier(MonthlyProductRankingJobConfig.JOB_NAME)
    private Job job;
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
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("윤년 2월의 첫날부터 말일까지 집계하고 다른 월 데이터는 제외한다.")
    @Test
    void aggregatesMonthlyBoundary() throws Exception {
        insertHourly(LocalDate.of(2024, 1, 31), 1L, 100, 100, 100);
        insertHourly(LocalDate.of(2024, 2, 1), 1L, 3, 2, 4);
        insertHourly(LocalDate.of(2024, 2, 29), 1L, 7, 5, 11);
        insertHourly(LocalDate.of(2024, 3, 1), 1L, 100, 100, 100);

        var execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("requestDate", "2024-02-10")
            .addLong("executionNonce", 1L)
            .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var row = jdbcTemplate.queryForMap("""
            SELECT period_start, period_end, view_count, like_count, sales_count, score, rank_position
            FROM mv_product_rank_monthly
            WHERE period_start = ? AND product_id = 1
            """, LocalDate.of(2024, 2, 1));
        assertThat(row.get("period_start").toString()).isEqualTo("2024-02-01");
        assertThat(row.get("period_end").toString()).isEqualTo("2024-02-29");
        assertThat(((Number) row.get("view_count")).longValue()).isEqualTo(7);
        assertThat(((Number) row.get("like_count")).longValue()).isEqualTo(10);
        assertThat(((Number) row.get("sales_count")).longValue()).isEqualTo(15);
        assertThat(((Number) row.get("score")).doubleValue()).isEqualTo(13.2);
        assertThat(((Number) row.get("rank_position")).longValue()).isEqualTo(1);
    }

    @DisplayName("월간 랭킹도 동점은 productId 오름차순으로 TOP 100만 완전히 교체한다.")
    @Test
    void replacesMonthlyRankingWithDeterministicTop100() throws Exception {
        LocalDate monthStart = LocalDate.of(2026, 6, 1);
        for (long productId = 1; productId <= 105; productId++) {
            insertHourly(monthStart, productId, 0, 0, productId <= 2 ? 10 : 1);
        }
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_monthly
                (period_start, period_end, product_id, rank_position, score,
                 view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, ?, 999, 1, 33, 0, 0, 0, NOW(6), NOW(6))
            """, monthStart, monthStart.withDayOfMonth(monthStart.lengthOfMonth()));

        assertThat(launchStatus(LocalDate.of(2026, 6, 15), 11L))
            .isEqualTo(BatchStatus.COMPLETED);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE period_start = ?",
            Integer.class,
            monthStart
        )).isEqualTo(100);
        assertThat(jdbcTemplate.queryForList("""
            SELECT product_id
            FROM mv_product_rank_monthly
            WHERE period_start = ?
            ORDER BY rank_position
            LIMIT 2
            """, Long.class, monthStart)).containsExactly(1L, 2L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT product_id
            FROM mv_product_rank_monthly
            WHERE period_start = ? AND rank_position = 100
            """, Long.class, monthStart)).isEqualTo(100L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM mv_product_rank_monthly
            WHERE period_start = ? AND product_id IN (101, 102, 103, 104, 105, 999)
            """, Integer.class, monthStart)).isZero();
    }

    @DisplayName("월간 랭킹 재실행의 0건 결과도 기존 기간을 완전히 교체한다.")
    @Test
    void replacesMonthlyRankingWithEmptyResult() throws Exception {
        LocalDate monthStart = LocalDate.of(2026, 6, 1);
        insertHourly(monthStart, 1L, 0, 0, 1);
        assertThat(launchStatus(monthStart.plusDays(10), 21L))
            .isEqualTo(BatchStatus.COMPLETED);
        jdbcTemplate.update(
            "DELETE FROM product_metric_hourly WHERE metric_date BETWEEN ? AND ?",
            monthStart,
            monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        );

        assertThat(launchStatus(monthStart.plusDays(20), 22L))
            .isEqualTo(BatchStatus.COMPLETED);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE period_start = ?",
            Integer.class,
            monthStart
        )).isZero();
    }

    @DisplayName("월간 Job도 같은 JobInstance restart 시 최초 staging snapshot으로 발행한다.")
    @Test
    void restartsMonthlyRankingFromOriginalSnapshot() throws Exception {
        LocalDate monthStart = LocalDate.of(2026, 6, 1);
        for (long productId = 1; productId <= 101; productId++) {
            insertHourly(monthStart, productId, 0, 0, 200 - productId);
        }
        JobParameters parameters = new JobParametersBuilder()
            .addString("requestDate", monthStart.plusDays(10).toString())
            .addLong("executionNonce", 31L)
            .toJobParameters();
        jdbcTemplate.execute("""
            ALTER TABLE product_rank_staging
            ADD CONSTRAINT ck_fail_monthly_second_chunk
            CHECK (product_id <> 101 OR score = 0)
            """);

        var failed = jobLauncherTestUtils.launchJob(parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        Long jobInstanceId = failed.getJobInstance().getInstanceId();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_staging",
            Integer.class
        )).isEqualTo(101);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT sales_count
            FROM product_rank_staging
            WHERE product_id = 101
            """, Long.class)).isEqualTo(99L);
        jdbcTemplate.execute("""
            ALTER TABLE product_rank_staging
            DROP CHECK ck_fail_monthly_second_chunk
            """);
        jdbcTemplate.update("""
            UPDATE product_metric_hourly
            SET sales_count = 10000
            WHERE metric_date = ? AND product_id = 101
            """, monthStart);

        var restarted = jobLauncherTestUtils.launchJob(parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(jobInstanceId);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM mv_product_rank_monthly
            WHERE period_start = ? AND product_id = 101
            """, Integer.class, monthStart)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_staging",
            Integer.class
        )).isZero();
    }

    @DisplayName("월간 publish 실패도 target 삭제를 rollback하고 기존 MV를 유지한다.")
    @Test
    void rollsBackMonthlyReplacementWhenPublishFails() throws Exception {
        LocalDate monthStart = LocalDate.of(2026, 6, 1);
        insertHourly(monthStart, 1L, 0, 0, 10);
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_monthly
                (period_start, period_end, product_id, rank_position, score,
                 view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, ?, 999, 1, 33, 0, 0, 0, NOW(6), NOW(6))
            """, monthStart, monthStart.withDayOfMonth(monthStart.lengthOfMonth()));
        jdbcTemplate.execute("""
            ALTER TABLE mv_product_rank_monthly
            ADD CONSTRAINT ck_fail_monthly_publish CHECK (product_id <> 1)
            """);

        BatchStatus status;
        try {
            status = launchStatus(monthStart.plusDays(10), 41L);
        } finally {
            jdbcTemplate.execute("""
                ALTER TABLE mv_product_rank_monthly
                DROP CHECK ck_fail_monthly_publish
                """);
        }

        assertThat(status).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbcTemplate.queryForMap("""
            SELECT product_id, rank_position, score
            FROM mv_product_rank_monthly
            WHERE period_start = ?
            """, monthStart)).satisfies(row -> {
                assertThat(((Number) row.get("product_id")).longValue()).isEqualTo(999);
                assertThat(((Number) row.get("rank_position")).longValue()).isEqualTo(1);
                assertThat(((Number) row.get("score")).doubleValue()).isEqualTo(33.0);
            });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_job_lock",
            Integer.class
        )).isZero();
    }

    private BatchStatus launchStatus(LocalDate date, long nonce) throws Exception {
        return jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("requestDate", date.toString())
            .addLong("executionNonce", nonce)
            .toJobParameters()).getStatus();
    }

    private void insertHourly(
        LocalDate date,
        long productId,
        long likes,
        long views,
        long sales
    ) {
        jdbcTemplate.update("""
            INSERT INTO product_metric_hourly
                (metric_date, metric_hour, product_id, like_count, view_count, sales_count, created_at, updated_at)
            VALUES (?, 12, ?, ?, ?, ?, NOW(6), NOW(6))
            """, date, productId, likes, views, sales);
    }
}
