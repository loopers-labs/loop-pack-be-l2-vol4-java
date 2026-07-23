package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankAggregationJobConfig;
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

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + ProductRankAggregationJobConfig.JOB_NAME)
class ProductRankAggregationJobE2ETest {

    // 2024-01-01(월) ~ 2024-01-07(일) 이 한 주다. 주 중간 날짜를 기준일로 넘긴다.
    private static final LocalDate BASE_DATE = LocalDate.of(2024, 1, 3);
    private static final LocalDate WEEK_START = LocalDate.of(2024, 1, 1);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ProductRankAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    // 배치는 순수 JDBC 로 소스(product_metrics_daily)를 읽고 MV(mv_product_rank_weekly)에 적재한다(JPA 엔티티 없음).
    // 두 테이블 모두 배치 모듈이 매핑하지 않으므로, 테스트가 직접 provisioning 하고 JDBC 로 시딩/검증한다.
    // (운영은 마이그레이션/DBA 가, 로컬은 사전에 수동으로 테이블을 생성한다는 전제)
    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS product_metrics_daily (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_id BIGINT NOT NULL,
                metric_date DATE NOT NULL,
                like_count BIGINT NOT NULL,
                order_count BIGINT NOT NULL,
                view_count BIGINT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                week_start_date DATE NOT NULL,
                product_id BIGINT NOT NULL,
                score BIGINT NOT NULL,
                ranking INT NOT NULL,
                created_at DATETIME NOT NULL,
                updated_at DATETIME NOT NULL,
                UNIQUE KEY uk_mv_product_rank_weekly_date_product (week_start_date, product_id)
            )
            """);
        truncateTables();
    }

    @AfterEach
    void tearDown() {
        truncateTables();
    }

    private void truncateTables() {
        jdbcTemplate.update("TRUNCATE TABLE product_metrics_daily");
        jdbcTemplate.update("TRUNCATE TABLE mv_product_rank_weekly");
    }

    @DisplayName("baseDate/period 파라미터가 없으면, 배치는 실패한다.")
    @Test
    void fails_whenRequiredParametersAreMissing() throws Exception {
        // given
        jobLauncherTestUtils.setJob(job);

        // when
        var jobExecution = jobLauncherTestUtils.launchJob();

        // then
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    @DisplayName("주간 랭킹 배치는 해당 주 일별 집계를 상품별로 합산해 점수순 TOP 랭킹을 MV 에 적재한다.")
    @Test
    void aggregatesWeeklyRankingIntoMv() throws Exception {
        // given - (productId, metricDate, likeCount, orderCount, viewCount)
        // product 1: view 10(01-01) + order 5(01-03) → 10*1 + 5*10 = 60
        seedDailyMetric(1L, LocalDate.of(2024, 1, 1), 0L, 0L, 10L);
        seedDailyMetric(1L, LocalDate.of(2024, 1, 3), 0L, 5L, 0L);
        // product 2: order 10(01-05) → 10*10 = 100 (최고점)
        seedDailyMetric(2L, LocalDate.of(2024, 1, 5), 0L, 10L, 0L);
        // product 3: like 3(01-04) → 3*3 = 9
        seedDailyMetric(3L, LocalDate.of(2024, 1, 4), 3L, 0L, 0L);
        // product 4: 모든 건수 0 → 점수 0 이므로 랭킹에서 제외
        seedDailyMetric(4L, LocalDate.of(2024, 1, 6), 0L, 0L, 0L);
        // product 5: 주 범위 밖(01-08) → 집계 제외
        seedDailyMetric(5L, LocalDate.of(2024, 1, 8), 0L, 100L, 0L);
        jobLauncherTestUtils.setJob(job);

        // when
        var jobParameters = new JobParametersBuilder()
            .addLocalDate("baseDate", BASE_DATE)
            .addString("period", "WEEKLY")
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        List<RankRow> ranking = jdbcTemplate.query(
            "SELECT product_id, score, ranking FROM mv_product_rank_weekly WHERE week_start_date = ? ORDER BY ranking",
            (rs, rowNum) -> new RankRow(rs.getLong("product_id"), rs.getLong("score"), rs.getInt("ranking")),
            WEEK_START
        );
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(ranking).hasSize(3),
            () -> assertThat(ranking).extracting(RankRow::productId).containsExactly(2L, 1L, 3L),
            () -> assertThat(ranking).extracting(RankRow::score).containsExactly(100L, 60L, 9L),
            () -> assertThat(ranking).extracting(RankRow::ranking).containsExactly(1, 2, 3)
        );
    }

    private void seedDailyMetric(long productId, LocalDate metricDate, long likeCount, long orderCount, long viewCount) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_daily (product_id, metric_date, like_count, order_count, view_count)"
                + " VALUES (?, ?, ?, ?, ?)",
            productId, metricDate, likeCount, orderCount, viewCount
        );
    }

    private record RankRow(long productId, long score, int ranking) {
    }
}
