package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.WeeklyProductRankingJobConfig;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyProductRankingJobConfig.JOB_NAME)
class WeeklyProductRankingJobE2ETest {
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier(WeeklyProductRankingJobConfig.JOB_NAME)
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

    @DisplayName("requestDate가 없거나 아직 끝나지 않은 주간은 실행하지 않는다.")
    @Test
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob())
            .hasMessageContaining("requestDate");

        LocalDate today = LocalDate.now(com.loopers.ranking.DailyRankingKey.ZONE_ID);
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("requestDate", today.toString())
            .addLong("executionNonce", 1L)
            .toJobParameters()))
            .hasMessageContaining("종료");
    }

    @DisplayName("같은 주간의 실행 lock이 있으면 Job을 실패시키고 기존 MV를 유지한다.")
    @Test
    void rejectsConcurrentExecution() throws Exception {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        jdbcTemplate.update("""
            INSERT INTO product_rank_job_lock
                (period_type, period_start, owner_id, expires_at, created_at, updated_at)
            VALUES ('WEEKLY', ?, 999, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6))
            """, monday);
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_weekly
                (period_start, period_end, product_id, rank_position, score,
                 view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, ?, 1, 1, 10, 0, 0, 0, NOW(6), NOW(6))
            """, monday, monday.plusDays(6));

        assertThat(launchStatus(monday.plusDays(2), 9L)).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_weekly WHERE period_start = ?",
            Double.class,
            monday
        )).isEqualTo(10.0);
    }

    @DisplayName("월요일부터 일요일까지 합산하고 동점은 productId 오름차순으로 TOP 100만 교체한다.")
    @Test
    void aggregatesWeeklyRankingDeterministically() throws Exception {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        insertHourly(monday.minusDays(1), 1L, 0, 0, 999);
        insertHourly(monday, 1L, 0, 0, 5);
        insertHourly(monday.plusDays(6), 1L, 0, 0, 5);
        insertHourly(monday.plusDays(2), 2L, 0, 0, 10);
        for (long productId = 3; productId <= 105; productId++) {
            insertHourly(monday.plusDays(1), productId, 0, 0, 1);
        }
        insertHourly(monday.plusDays(7), 999L, 0, 0, 999);
        insertHourly(monday.plusDays(2), 106L, 0, 0, 0);
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_weekly
                (period_start, period_end, product_id, rank_position, score,
                 view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, ?, 888, 1, 1, 0, 0, 0, NOW(6), NOW(6))
            """, monday.minusWeeks(1), monday.minusDays(1));

        assertThat(launchStatus(monday.plusDays(3), 1L)).isEqualTo(BatchStatus.COMPLETED);
        var firstTwo = jdbcTemplate.queryForList("""
            SELECT product_id, rank_position
            FROM mv_product_rank_weekly
            WHERE period_start = ?
            ORDER BY rank_position
            LIMIT 2
            """, monday);
        assertThat(firstTwo).extracting(row -> ((Number) row.get("product_id")).longValue())
            .containsExactly(1L, 2L);
        var first = jdbcTemplate.queryForMap("""
            SELECT score, view_count, like_count, sales_count
            FROM mv_product_rank_weekly
            WHERE period_start = ? AND product_id = 1
            """, monday);
        assertThat(((Number) first.get("score")).doubleValue()).isEqualTo(7.0);
        assertThat(((Number) first.get("view_count")).longValue()).isZero();
        assertThat(((Number) first.get("like_count")).longValue()).isZero();
        assertThat(((Number) first.get("sales_count")).longValue()).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_start = ?",
            Integer.class,
            monday
        )).isEqualTo(100);
        assertThat(jdbcTemplate.queryForMap("""
            SELECT product_id, rank_position
            FROM mv_product_rank_weekly
            WHERE period_start = ? AND rank_position = 100
            """, monday)).satisfies(row -> {
                assertThat(((Number) row.get("product_id")).longValue()).isEqualTo(100);
                assertThat(((Number) row.get("rank_position")).longValue()).isEqualTo(100);
            });
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM mv_product_rank_weekly
            WHERE product_id IN (101, 102, 103, 104, 105, 106, 999)
            """,
            Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_start = ? AND product_id = 888",
            Integer.class,
            monday.minusWeeks(1)
        )).isOne();
    }

    @DisplayName("같은 기간 재실행은 기존 결과를 완전히 교체하며 0건 결과도 반영한다.")
    @Test
    void replacesPeriodWithEmptyResult() throws Exception {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        insertHourly(monday, 1L, 0, 0, 1);
        assertThat(launchStatus(monday, 1L)).isEqualTo(BatchStatus.COMPLETED);
        jdbcTemplate.update("DELETE FROM product_metric_hourly WHERE metric_date BETWEEN ? AND ?",
            monday, monday.plusDays(6));

        assertThat(launchStatus(monday.plusDays(5), 2L)).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_start = ?",
            Integer.class,
            monday
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_staging",
            Integer.class
        )).isZero();
    }

    @DisplayName("두 번째 chunk 실패 후 같은 JobInstance를 restart하면 앞 chunk를 유지하고 정확한 TOP 100을 발행한다.")
    @Test
    void restartsFromCommittedChunk() throws Exception {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        for (long productId = 1; productId <= 101; productId++) {
            insertHourly(monday, productId, 0, 0, 200 - productId);
        }
        JobParameters parameters = new JobParametersBuilder()
            .addString("requestDate", monday.plusDays(2).toString())
            .addLong("executionNonce", 31L)
            .toJobParameters();
        jdbcTemplate.execute("""
            ALTER TABLE product_rank_staging
            ADD CONSTRAINT ck_fail_weekly_second_chunk
            CHECK (product_id <> 101 OR score = 0)
            """);

        var failed = jobLauncherTestUtils.launchJob(parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        Long failedJobInstanceId = failed.getJobInstance().getInstanceId();
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
            DROP CHECK ck_fail_weekly_second_chunk
            """);
        jdbcTemplate.update("""
            UPDATE product_metric_hourly
            SET sales_count = 10000
            WHERE metric_date = ? AND product_id = 101
            """, monday);

        var restarted = jobLauncherTestUtils.launchJob(parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getInstanceId())
            .isEqualTo(failedJobInstanceId);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_start = ?",
            Integer.class,
            monday
        )).isEqualTo(100);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM mv_product_rank_weekly
            WHERE period_start = ? AND rank_position BETWEEN 1 AND 100
            """, Integer.class, monday)).isEqualTo(100);
        assertThat(jdbcTemplate.queryForMap("""
            SELECT product_id, rank_position, sales_count, score
            FROM mv_product_rank_weekly
            WHERE period_start = ? AND rank_position = 100
            """, monday)).satisfies(row -> {
                assertThat(((Number) row.get("product_id")).longValue()).isEqualTo(100);
                assertThat(((Number) row.get("rank_position")).longValue()).isEqualTo(100);
                assertThat(((Number) row.get("sales_count")).longValue()).isEqualTo(100);
                assertThat(((Number) row.get("score")).doubleValue()).isEqualTo(70.0);
            });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE product_id = 101",
            Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_staging",
            Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_job_lock",
            Integer.class
        )).isZero();
    }

    @DisplayName("보존기한이 지난 orphan staging만 정리하고 실행 중인 다른 기간의 staging은 보존한다.")
    @Test
    void cleansOnlyExpiredOrphanStaging() throws Exception {
        LocalDate activePeriodStart = LocalDate.of(2026, 6, 1);
        insertStaging("WEEKLY:2026-05-25:701", LocalDate.of(2026, 5, 25), 701L, 701L, 10);
        insertStaging("WEEKLY:2026-06-01:702", activePeriodStart, 702L, 702L, 10);
        jdbcTemplate.update("""
            INSERT INTO product_rank_job_lock
                (period_type, period_start, owner_id, expires_at, created_at, updated_at)
            VALUES ('WEEKLY', ?, 702, DATE_ADD(NOW(6), INTERVAL 1 HOUR), NOW(6), NOW(6))
            """, activePeriodStart);

        assertThat(launchStatus(LocalDate.of(2026, 7, 6), 702L))
            .isEqualTo(BatchStatus.COMPLETED);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_staging WHERE run_key = 'WEEKLY:2026-05-25:701'",
            Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_rank_staging WHERE run_key = 'WEEKLY:2026-06-01:702'",
            Integer.class
        )).isOne();
    }

    @DisplayName("publish insert 실패 시 target 삭제가 rollback되어 기존 MV를 유지하고 lock을 해제한다.")
    @Test
    void rollsBackTargetReplacementWhenPublishFails() throws Exception {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        insertHourly(monday, 1L, 0, 0, 10);
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_weekly
                (period_start, period_end, product_id, rank_position, score,
                 view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, ?, 999, 1, 33, 0, 0, 0, NOW(6), NOW(6))
            """, monday, monday.plusDays(6));
        jdbcTemplate.execute("""
            ALTER TABLE mv_product_rank_weekly
            ADD CONSTRAINT ck_fail_weekly_publish CHECK (product_id <> 1)
            """);

        BatchStatus status;
        try {
            status = launchStatus(monday.plusDays(2), 41L);
        } finally {
            jdbcTemplate.execute("""
                ALTER TABLE mv_product_rank_weekly
                DROP CHECK ck_fail_weekly_publish
                """);
        }

        assertThat(status).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbcTemplate.queryForMap("""
            SELECT product_id, rank_position, score
            FROM mv_product_rank_weekly
            WHERE period_start = ?
            """, monday)).satisfies(row -> {
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

    private void insertHourly(LocalDate date, long productId, long likes, long views, long sales) {
        jdbcTemplate.update("""
            INSERT INTO product_metric_hourly
                (metric_date, metric_hour, product_id, like_count, view_count, sales_count, created_at, updated_at)
            VALUES (?, 12, ?, ?, ?, ?, NOW(6), NOW(6))
            """, date, productId, likes, views, sales);
    }

    private void insertStaging(
        String runKey,
        LocalDate periodStart,
        long jobInstanceId,
        long productId,
        int daysOld
    ) {
        jdbcTemplate.update("""
            INSERT INTO product_rank_staging
                (run_key, period_type, period_start, job_instance_id,
                 product_id, score, view_count, like_count, sales_count, created_at, updated_at)
            VALUES (?, 'WEEKLY', ?, ?, ?, 0, 0, 0, 1,
                    DATE_SUB(NOW(6), INTERVAL ? DAY), DATE_SUB(NOW(6), INTERVAL ? DAY))
            """, runKey, periodStart, jobInstanceId, productId, daysOld, daysOld);
    }
}
