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
 * 집계 → 매핑 → 적재까지 한 번에 본다.
 * 기간 합산, 가중치 점수, 동점 tie-break, 노출 불가 상품 제외, 상위 150 컷이 모두 여기서 드러난다.
 * 테스트마다 targetDate 를 그 주의 다른 날로 준다 — period_key 는 같은 2026-W30 이지만
 * JobInstance 가 갈라져 완료 가드에 걸리지 않는다(배치 메타테이블은 테스트 간에 비워지지 않는다).
 * products(commerce-api)와 product_metrics(streamer)는 배치 classpath 에 엔티티가 없다
 * — JDBC 로만 읽으므로 테이블만 만들어 둔다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class WeeklyRankingAggregationE2ETest {

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
                    id bigint NOT NULL AUTO_INCREMENT,
                    brand_id bigint NOT NULL,
                    name varchar(255) NOT NULL,
                    price bigint NOT NULL,
                    status varchar(32) NOT NULL,
                    like_count bigint NOT NULL DEFAULT 0,
                    created_at datetime(6) NOT NULL,
                    updated_at datetime(6) NOT NULL,
                    deleted_at datetime(6) NULL,
                    PRIMARY KEY (id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics (
                    stat_date date NOT NULL,
                    product_id bigint NOT NULL,
                    view_count bigint NOT NULL,
                    like_count bigint NOT NULL,
                    sales_count bigint NOT NULL,
                    updated_at datetime(6) NULL,
                    PRIMARY KEY (stat_date, product_id)
                )
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

    private void product(long id, String status, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO products (id, brand_id, name, price, status, like_count, created_at, updated_at, deleted_at)
                VALUES (?, 1, concat('p', ?), 1000, ?, 0, NOW(), NOW(), ?)
                """, id, id, status, deleted ? java.sql.Timestamp.valueOf("2026-01-01 00:00:00") : null);
    }

    private void metric(LocalDate statDate, long productId, long view, long like, long sales) {
        jdbcTemplate.update("""
                INSERT INTO product_metrics (stat_date, product_id, view_count, like_count, sales_count, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """, statDate, productId, view, like, sales);
    }

    private List<Map<String, Object>> mvRows() {
        return jdbcTemplate.queryForList("""
                SELECT product_id, score, view_count, like_count, sales_count
                FROM mv_product_rank_weekly WHERE period_key = '2026-W30'
                ORDER BY score DESC, product_id ASC
                """);
    }

    /**
     * JobExecution 을 반환하면 안 된다 — @SpringBatchTest 의 JobScopeTestExecutionListener 가
     * 테스트 클래스에서 JobExecution 을 반환하는 메서드를 찾아 인자 없이 호출한다.
     */
    private String runAndGetExitCode(String targetDate) throws Exception {
        return jobLauncherTestUtils.launchJob(
                new JobParametersBuilder().addString("targetDate", targetDate).toJobParameters())
                .getExitStatus().getExitCode();
    }

    @Test
    @DisplayName("기간 안의 일별 카운트를 합산해 가중치 점수로 순위를 낸다")
    void givenMetricsAcrossWeek_whenAggregated_thenSummedAndScored() throws Exception {
        product(100L, "ON_SALE", false);
        product(200L, "ON_SALE", false);
        metric(MONDAY, 100L, 10, 0, 0);
        metric(MONDAY.plusDays(1), 100L, 10, 0, 0);
        metric(MONDAY, 200L, 0, 0, 5);

        String exitCode = runAndGetExitCode("20260720");

        assertThat(exitCode).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rows = mvRows();
        assertThat(rows).hasSize(2);
        // 200: sales 5 * 0.6 = 3.0 / 100: view 20 * 0.1 = 2.0
        assertThat(rows.get(0)).containsEntry("product_id", 200L);
        assertThat((Double) rows.get(0).get("score")).isEqualTo(3.0);
        assertThat(rows.get(1)).containsEntry("product_id", 100L);
        assertThat((Double) rows.get(1).get("score")).isEqualTo(2.0);
        assertThat(rows.get(1)).containsEntry("view_count", 20L);
    }

    @Test
    @DisplayName("기간 밖의 날짜는 합산하지 않는다")
    void givenMetricsOutsideRange_whenAggregated_thenExcluded() throws Exception {
        product(100L, "ON_SALE", false);
        metric(MONDAY, 100L, 10, 0, 0);
        metric(MONDAY.minusDays(1), 100L, 999, 0, 0);
        metric(MONDAY.plusDays(7), 100L, 999, 0, 0);

        runAndGetExitCode("20260721");

        assertThat(mvRows()).hasSize(1);
        assertThat(mvRows().get(0)).containsEntry("view_count", 10L);
    }

    @Test
    @DisplayName("삭제·판매중지 상품은 랭킹에서 빠진다")
    void givenHiddenProducts_whenAggregated_thenExcluded() throws Exception {
        product(100L, "ON_SALE", false);
        product(200L, "SUSPENDED", false);
        product(300L, "ON_SALE", true);
        metric(MONDAY, 100L, 10, 0, 0);
        metric(MONDAY, 200L, 999, 0, 0);
        metric(MONDAY, 300L, 999, 0, 0);

        runAndGetExitCode("20260722");

        assertThat(mvRows()).hasSize(1);
        assertThat(mvRows().get(0)).containsEntry("product_id", 100L);
    }

    @Test
    @DisplayName("동점이면 product_id 오름차순으로 순서를 고정한다")
    void givenTiedScores_whenAggregated_thenOrderedByProductId() throws Exception {
        for (long id : List.of(300L, 100L, 200L)) {
            product(id, "ON_SALE", false);
            metric(MONDAY, id, 10, 0, 0);
        }

        runAndGetExitCode("20260723");

        assertThat(mvRows()).extracting(r -> r.get("product_id"))
                .containsExactly(100L, 200L, 300L);
    }

    @Test
    @DisplayName("상위 150 개만 적재한다")
    void givenMoreThanLimit_whenAggregated_thenOnlyTop150Stored() throws Exception {
        for (long id = 1; id <= 160; id++) {
            product(id, "ON_SALE", false);
            metric(MONDAY, id, id, 0, 0);
        }

        runAndGetExitCode("20260724");

        List<Map<String, Object>> rows = mvRows();
        assertThat(rows).hasSize(150);
        assertThat(rows.get(0)).containsEntry("product_id", 160L);
        assertThat(rows.get(149)).containsEntry("product_id", 11L);
    }
}
