package com.loopers.job.likecount;

import com.loopers.batch.job.likecount.LikeCountSyncJobConfig;
import com.loopers.config.redis.RedisConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 수 동기화 배치 E2E. Redis에 누적된 증감분이 product.like_count로 반영되는지 검증한다.
 * commerce-batch에는 도메인 엔티티가 없어 product 테이블을 테스트에서 직접 만든다(배치는 네이티브 SQL로 갱신).
 */
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.batch.job.name=" + LikeCountSyncJobConfig.JOB_NAME)
class LikeCountSyncJobE2ETest {

    private static final String DELTA_KEY_PREFIX = "like:count:delta:";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(LikeCountSyncJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS product (id BIGINT PRIMARY KEY, like_count BIGINT NOT NULL, deleted_at TIMESTAMP NULL)");
        jdbcTemplate.update("DELETE FROM product");
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        jdbcTemplate.execute("DROP TABLE IF EXISTS product");
    }

    @DisplayName("Redis에 쌓인 좋아요 증감분이 product.like_count에 더해지고 증감분 키는 제거된다")
    @Test
    void appliesDeltaToColumn() throws Exception {
        // arrange
        jdbcTemplate.update("INSERT INTO product (id, like_count, deleted_at) VALUES (1, 5, NULL)");
        masterRedisTemplate.opsForValue().set(DELTA_KEY_PREFIX + 1, "3");

        // act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        Long likeCount = jdbcTemplate.queryForObject("SELECT like_count FROM product WHERE id = 1", Long.class);
        assertThat(likeCount).isEqualTo(8L);
        assertThat(masterRedisTemplate.opsForValue().get(DELTA_KEY_PREFIX + 1)).isNull();
    }

    @DisplayName("음수 증감분이 현재 카운트를 넘어도 like_count는 0 미만으로 내려가지 않는다")
    @Test
    void floorsAtZero() throws Exception {
        // arrange
        jdbcTemplate.update("INSERT INTO product (id, like_count, deleted_at) VALUES (1, 2, NULL)");
        masterRedisTemplate.opsForValue().set(DELTA_KEY_PREFIX + 1, "-5");

        // act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        Long likeCount = jdbcTemplate.queryForObject("SELECT like_count FROM product WHERE id = 1", Long.class);
        assertThat(likeCount).isZero();
        assertThat(masterRedisTemplate.opsForValue().get(DELTA_KEY_PREFIX + 1)).isNull();
    }
}
