package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.DailyRankingSnapshotJobConfig;
import com.loopers.batch.job.ranking.DailyRankingExecutionLock;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + DailyRankingSnapshotJobConfig.JOB_NAME)
class DailyRankingSnapshotJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier(DailyRankingSnapshotJobConfig.JOB_NAME)
    private Job job;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisCleanUp redisCleanUp;
    @Autowired
    private DailyRankingExecutionLock executionLock;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("requestDate가 없으면 일간 랭킹 스냅샷 Job이 실패한다.")
    @Test
    void failsWithoutRequestDate() throws Exception {
        var execution = jobLauncherTestUtils.launchJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @DisplayName("한국 시간 기준 오늘이나 미래 날짜는 스냅샷 대상으로 허용하지 않는다.")
    @Test
    void rejectsOpenDate() throws Exception {
        LocalDate today = LocalDate.now(DailyRankingKey.ZONE_ID);
        var parameters = new JobParametersBuilder()
            .addString("requestDate", today.toString())
            .addLong("executionNonce", 2L)
            .toJobParameters();

        var execution = jobLauncherTestUtils.launchJob(parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @DisplayName("같은 날짜의 실행 lock이 잡혀 있으면 기존 canonical을 유지하고 Job이 실패한다.")
    @Test
    void rejectsConcurrentExecutionForSameDate() throws Exception {
        LocalDate date = LocalDate.now(DailyRankingKey.ZONE_ID).minusDays(2);
        String key = DailyRankingKey.from(date);
        redisTemplate.opsForZSet().add(key, "old", 99.0);
        assertThat(executionLock.acquire(date, 9_001L)).isTrue();
        var parameters = new JobParametersBuilder()
            .addString("requestDate", date.toString())
            .addLong("executionNonce", 3L)
            .toJobParameters();

        var execution = jobLauncherTestUtils.launchJob(parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(redisTemplate.opsForZSet().score(key, "old")).isEqualTo(99.0);
        assertThat(executionLock.release(date, 9_002L)).isFalse();
        assertThat(executionLock.acquire(date, 9_002L)).isFalse();
        assertThat(executionLock.release(date, 9_001L)).isTrue();
    }

    @DisplayName("시간별 지표를 날짜와 상품으로 합산해 음수와 0을 포함한 원점수 스냅샷을 발행한다.")
    @Test
    void publishesDailyAbsoluteRankingSnapshot() throws Exception {
        LocalDate date = LocalDate.now(DailyRankingKey.ZONE_ID).minusDays(1);
        insertHourly(date, 9, 1L, 2, 4, 1);
        insertHourly(date, 10, 1L, 3, 6, 1);
        insertHourly(date, 10, 2L, -1, 0, 0);
        insertHourly(date, 10, 4L, 0, 0, 0);
        insertHourly(date.minusDays(1), 10, 3L, 100, 100, 100);
        String key = DailyRankingKey.from(date);
        redisTemplate.opsForZSet().add(key, "stale", 100.0);

        var parameters = new JobParametersBuilder()
            .addString("requestDate", date.toString())
            .addLong("executionNonce", 1L)
            .toJobParameters();
        var execution = jobLauncherTestUtils.launchJob(parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(redisTemplate.opsForZSet().score(key, "1")).isEqualTo(3.4);
        assertThat(redisTemplate.opsForZSet().score(key, "2")).isEqualTo(-0.2);
        assertThat(redisTemplate.opsForZSet().score(key, "4")).isZero();
        assertThat(redisTemplate.opsForZSet().score(key, "3")).isNull();
        assertThat(redisTemplate.opsForZSet().score(key, "stale")).isNull();
        assertThat(executionLock.acquire(date, 9_003L)).isTrue();
        assertThat(executionLock.release(date, 9_003L)).isTrue();
    }

    private void insertHourly(
        LocalDate date,
        int hour,
        long productId,
        long likeCount,
        long viewCount,
        long salesCount
    ) {
        jdbcTemplate.update("""
            INSERT INTO product_metric_hourly
                (metric_date, metric_hour, product_id, like_count, view_count, sales_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
            """, date, hour, productId, likeCount, viewCount, salesCount);
    }
}
