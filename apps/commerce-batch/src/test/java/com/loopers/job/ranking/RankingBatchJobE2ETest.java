package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingBatchJobConfig;
import com.loopers.domain.ranking.batch.RankingBatchLock;
import com.loopers.domain.ranking.batch.RankingBatchLockKeys;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@TestPropertySource(properties = "spring.batch.job.name=" + RankingBatchJobConfig.JOB_NAME)
class RankingBatchJobE2ETest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RankingBatchJobConfig.JOB_NAME) private Job job;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;
    @Autowired private RankingBatchLock rankingBatchLock;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
        // BATCH_* 테이블은 JPA 엔티티가 아니라 DatabaseCleanUp이 지우지 않는다.
        // 같은 (period, periodKey) 조합을 여러 테스트에서 재사용해도 JobInstanceAlreadyCompleteException이
        // 나지 않도록 매 테스트 후 직접 비워준다.
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        List.of(
            "BATCH_STEP_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_CONTEXT",
            "BATCH_STEP_EXECUTION", "BATCH_JOB_EXECUTION_PARAMS",
            "BATCH_JOB_EXECUTION", "BATCH_JOB_INSTANCE"
        ).forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + table));
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private void insertDailyMetrics(LocalDate date, Long productId, long orderCount, long likeCount, long viewCount) {
        jdbcTemplate.update(
            """
                INSERT INTO product_daily_metrics (metric_date, product_id, order_count, like_count, view_count, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """,
            date, productId, orderCount, likeCount, viewCount
        );
    }

    private List<Map<String, Object>> findWeeklyRows(String periodKey) {
        return jdbcTemplate.queryForList(
            "SELECT product_id, rank_position, score FROM mv_product_rank_weekly WHERE period_key = ? ORDER BY rank_position",
            periodKey
        );
    }

    @DisplayName("WEEKLY Job을 실행할 때,")
    @Nested
    class WeeklyJob {

        @DisplayName("기간 내 product_daily_metrics를 집계해 mv_product_rank_weekly에 순위를 게시한다.")
        @Test
        void publishesWeeklyRanking() throws Exception {
            insertDailyMetrics(LocalDate.of(2026, 7, 13), 100L, 10, 5, 20);
            insertDailyMetrics(LocalDate.of(2026, 7, 15), 100L, 5, 0, 0);
            insertDailyMetrics(LocalDate.of(2026, 7, 14), 200L, 1, 1, 1);
            insertDailyMetrics(LocalDate.of(2026, 7, 20), 100L, 999, 999, 999);

            jobLauncherTestUtils.setJob(job);
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("periodKey", "2026W29")
                .toJobParameters();

            JobExecution execution = jobLauncherTestUtils.launchJob(parameters);

            assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            List<Map<String, Object>> rows = findWeeklyRows("2026W29");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).get("product_id")).isEqualTo(100L);
            assertThat(rows.get(0).get("rank_position")).isEqualTo(1);
            assertThat(rows.get(1).get("product_id")).isEqualTo(200L);
        }

        @DisplayName("해당 기간에 데이터가 없으면 빈 랭킹으로 완료된다.")
        @Test
        void completesWithEmptyRanking_whenNoDataInRange() throws Exception {
            jobLauncherTestUtils.setJob(job);
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("periodKey", "2026W01")
                .toJobParameters();

            JobExecution execution = jobLauncherTestUtils.launchJob(parameters);

            assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            assertThat(findWeeklyRows("2026W01")).isEmpty();
        }

        @DisplayName("같은 periodKey로 다시 실행하면 기존 순위를 새 결과로 교체한다.")
        @Test
        void replacesExistingRanking_whenRunAgainForSamePeriod() throws Exception {
            insertDailyMetrics(LocalDate.of(2026, 7, 13), 100L, 10, 0, 0);
            jobLauncherTestUtils.setJob(job);
            JobParameters firstRun = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("periodKey", "2026W29")
                .toJobParameters();
            jobLauncherTestUtils.launchJob(firstRun);

            jdbcTemplate.update("DELETE FROM product_daily_metrics");
            insertDailyMetrics(LocalDate.of(2026, 7, 14), 500L, 1, 0, 0);
            JobParameters secondRun = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("periodKey", "2026W29")
                .addLong("run.id", 2L) // JobLauncherTestUtils는 RunIdIncrementer를 자동 적용하지 않으므로 직접 구분
                .toJobParameters();
            JobExecution execution = jobLauncherTestUtils.launchJob(secondRun);

            assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            List<Map<String, Object>> rows = findWeeklyRows("2026W29");
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("product_id")).isEqualTo(500L);
        }
    }

    @DisplayName("MONTHLY Job을 실행할 때,")
    @Nested
    class MonthlyJob {

        @DisplayName("기간 내 product_daily_metrics를 집계해 mv_product_rank_monthly에 순위를 게시한다.")
        @Test
        void publishesMonthlyRanking() throws Exception {
            insertDailyMetrics(LocalDate.of(2026, 7, 1), 300L, 10, 0, 0);
            insertDailyMetrics(LocalDate.of(2026, 7, 31), 300L, 5, 0, 0);

            jobLauncherTestUtils.setJob(job);
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "MONTHLY")
                .addString("periodKey", "202607")
                .toJobParameters();

            JobExecution execution = jobLauncherTestUtils.launchJob(parameters);

            assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT product_id FROM mv_product_rank_monthly WHERE period_key = ?", "202607"
                    );
                    assertThat(rows).hasSize(1);
                    assertThat(rows.get(0).get("product_id")).isEqualTo(300L);
                }
            );
        }
    }

    @DisplayName("잘못된 Job 파라미터로 실행하면,")
    @Nested
    class InvalidParameters {

        @DisplayName("JobParametersInvalidException이 발생하고 아무것도 게시되지 않는다.")
        @Test
        void throwsJobParametersInvalidException() {
            jobLauncherTestUtils.setJob(job);
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "DAILY")
                .addString("periodKey", "2026W29")
                .toJobParameters();

            assertThatExceptionOfType(JobParametersInvalidException.class)
                .isThrownBy(() -> jobLauncherTestUtils.launchJob(parameters));
        }
    }

    @DisplayName("같은 (period, periodKey)에 대해 이미 실행 중인 배치가 있을 때,")
    @Nested
    class ConcurrentExecution {

        @DisplayName("새 실행은 락을 얻지 못해 FAILED로 종료되고 아무것도 게시하지 않는다.")
        @Test
        void failsWithoutPublishing_whenLockIsAlreadyHeld() throws Exception {
            String periodKey = "2026W31";
            String lockKey = RankingBatchLockKeys.of("WEEKLY", periodKey);
            rankingBatchLock.tryLock(lockKey, UUID.randomUUID().toString(), Duration.ofMinutes(5));
            insertDailyMetrics(LocalDate.of(2026, 7, 27), 100L, 10, 5, 20);

            jobLauncherTestUtils.setJob(job);
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("periodKey", periodKey)
                .toJobParameters();

            JobExecution execution = jobLauncherTestUtils.launchJob(parameters);

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(findWeeklyRows(periodKey)).isEmpty();
        }
    }
}
