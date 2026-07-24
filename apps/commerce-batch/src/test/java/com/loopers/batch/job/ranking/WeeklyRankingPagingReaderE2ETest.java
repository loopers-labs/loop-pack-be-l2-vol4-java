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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ranking.reader.type=PAGING 으로도 Job 이 끝까지 돈다.
 * 설정만 바꿔 리더를 전환한다는 계약을 검증한다 — 165개(2페이지 이상)를 넣어 페이지 경계를 넘긴다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false",
        "ranking.reader.type=PAGING"
})
class WeeklyRankingPagingReaderE2ETest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 20);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
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

    @Test
    @DisplayName("페이징 리더로도 상위 150 을 적재하고 완료한다")
    void givenPagingReader_whenLaunched_thenTop150Loaded() throws Exception {
        for (long id = 1; id <= 165; id++) {
            jdbcTemplate.update("""
                    INSERT INTO products (id, brand_id, name, price, status, like_count, created_at, updated_at, deleted_at)
                    VALUES (?, 1, concat('p', ?), 1000, 'ON_SALE', 0, NOW(), NOW(), NULL)
                    """, id, id);
            jdbcTemplate.update("""
                    INSERT INTO product_metrics (stat_date, product_id, view_count, like_count, sales_count, updated_at)
                    VALUES (?, ?, ?, 0, 0, NOW())
                    """, MONDAY, id, id * 10);
        }

        String exitCode = jobLauncherTestUtils.launchJob(
                        new JobParametersBuilder().addString("targetDate", "20260725").toJobParameters())
                .getExitStatus().getExitCode();

        assertThat(exitCode).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = '2026-W30'", Integer.class);
        assertThat(stored).isEqualTo(150);
        Long top = jdbcTemplate.queryForObject("""
                SELECT product_id FROM mv_product_rank_weekly WHERE period_key = '2026-W30'
                ORDER BY score DESC, product_id ASC LIMIT 1
                """, Long.class);
        assertThat(top).isEqualTo(165L);
    }
}
