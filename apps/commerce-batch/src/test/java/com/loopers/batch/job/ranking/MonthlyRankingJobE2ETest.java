package com.loopers.batch.job.ranking;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월간 Job 이 끝까지 돈다 — MONTHLY 기간(1일~말일), monthly 테이블 writer, 조건부 빈 배선을 한 번에 확인한다.
 * 주간과 공유 로직이 대부분이지만 대상 테이블·기간 범위가 갈리는 경로라 별도로 검증한다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class MonthlyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id bigint NOT NULL AUTO_INCREMENT, brand_id bigint NOT NULL, name varchar(255) NOT NULL,
                    price bigint NOT NULL, status varchar(32) NOT NULL, like_count bigint NOT NULL DEFAULT 0,
                    created_at datetime(6) NOT NULL, updated_at datetime(6) NOT NULL, deleted_at datetime(6) NULL,
                    PRIMARY KEY (id))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics (
                    stat_date date NOT NULL, product_id bigint NOT NULL, view_count bigint NOT NULL,
                    like_count bigint NOT NULL, sales_count bigint NOT NULL, updated_at datetime(6) NULL,
                    PRIMARY KEY (stat_date, product_id))
                """);
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM product_metrics");
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM product_metrics");
        databaseCleanUp.truncateAllTables();
    }

    private void product(long id) {
        jdbcTemplate.update("""
                INSERT INTO products (id, brand_id, name, price, status, like_count, created_at, updated_at, deleted_at)
                VALUES (?, 1, concat('p', ?), 1000, 'ON_SALE', 0, NOW(), NOW(), NULL)
                """, id, id);
    }

    private void metric(LocalDate statDate, long productId, long view) {
        jdbcTemplate.update("""
                INSERT INTO product_metrics (stat_date, product_id, view_count, like_count, sales_count, updated_at)
                VALUES (?, ?, ?, 0, 0, NOW())
                """, statDate, productId, view);
    }

    private List<Map<String, Object>> monthlyRows(String periodKey) {
        return jdbcTemplate.queryForList("""
                SELECT product_id, score, view_count FROM mv_product_rank_monthly WHERE period_key = ?
                ORDER BY score DESC, product_id ASC
                """, periodKey);
    }

    @Test
    @DisplayName("그 달 1일부터 말일까지를 합산해 monthly 테이블에 적재한다")
    void givenMetricsAcrossMonth_whenLaunched_thenSummedIntoMonthlyMv() throws Exception {
        product(100L);
        metric(LocalDate.of(2026, 7, 1), 100L, 10);
        metric(LocalDate.of(2026, 7, 31), 100L, 20);
        metric(LocalDate.of(2026, 6, 30), 100L, 999);   // 전달 말일 — 제외
        metric(LocalDate.of(2026, 8, 1), 100L, 999);    // 다음달 1일 — 제외

        String exitCode = jobLauncherTestUtils.launchJob(
                        new JobParametersBuilder().addString("targetDate", "20260715").toJobParameters())
                .getExitStatus().getExitCode();

        assertThat(exitCode).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rows = monthlyRows("2026-07");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("view_count", 30L);
        assertThat((Double) rows.get(0).get("score")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("주간 테이블은 건드리지 않는다")
    void givenMonthlyJob_whenLaunched_thenWeeklyTableUntouched() throws Exception {
        product(100L);
        metric(LocalDate.of(2026, 8, 10), 100L, 10);

        jobLauncherTestUtils.launchJob(
                new JobParametersBuilder().addString("targetDate", "20260810").toJobParameters());

        Integer weekly = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly", Integer.class);
        assertThat(weekly).isZero();
        assertThat(monthlyRows("2026-08")).hasSize(1);
    }
}
