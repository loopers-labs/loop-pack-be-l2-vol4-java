package com.loopers.job.productrank;

import com.loopers.batch.job.productrank.ProductRankAggregationJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 집계 Job E2E — 일간 메트릭을 넣고 Job 을 돌려 MV 가 "기간 안에서, 점수 순으로, TOP N 만" 만들어지는지 본다.
 *
 * <p>top-n 을 3 으로, chunk-size 를 2 로 낮춘다. 청크를 작게 두는 이유는 GROUP BY 페이징의
 * "다음 페이지" 쿼리 경로를 실제로 태우기 위해서다 — 기본값(500)이면 이 시나리오는 한 페이지에 끝나
 * 페이지 경계 버그가 상품 500개를 넘기기 전까지 드러나지 않는다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + ProductRankAggregationJobConfig.JOB_NAME,
    "ranking.top-n=3",
    "ranking.chunk-size=2"
})
@Sql(scripts = "/product-metrics-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProductRankAggregationJobE2ETest {

    // 2026-07-22(수) 가 속한 ISO 주: 07-20(월) ~ 07-26(일) = 2026-W30
    private static final String BASE_DATE = "20260722";
    private static final String WEEK_KEY = "2026-W30";
    private static final String MONTH_KEY = "2026-07";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ProductRankAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.update("DELETE FROM product_metrics");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");

        // 주간 구간(07-20 ~ 07-26) 안 — 점수: view*0.1 + likeDelta*0.2 + sales*0.7
        insert(1L, "2026-07-20", 0, 10, 0);    // 7.0
        insert(2L, "2026-07-21", 0, 5, 0);     // 합산 대상 (아래 로우와 함께 8건)
        insert(2L, "2026-07-22", 0, 3, 0);     // → 5.6
        insert(3L, "2026-07-23", 10, 0, 100);  // 12.0
        insert(4L, "2026-07-26", 0, 0, 30);    // 3.0
        insert(5L, "2026-07-24", 0, 0, 10);    // 1.0
        // 주간 구간 밖(전주 일요일) — 주간에서는 빠지고 월간에서는 들어와야 한다
        insert(6L, "2026-07-19", 0, 100, 0);   // 70.0
    }

    @DisplayName("주간 Job 은 그 주의 일간 메트릭만 합산해 점수 순 TOP N 을 MV 에 남긴다.")
    @Test
    void aggregatesWeeklyTopN() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(params(BASE_DATE, "WEEKLY", 1L));

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> rows = weeklyRows();
        // top-n=3 → 12.0(3번), 7.0(1번), 5.6(2번) 만 남는다
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("product_id")).containsExactly(3L, 1L, 2L);
        assertThat(rows).extracting(r -> r.get("rank_no")).containsExactly(1, 2, 3);
        assertThat(rows).allSatisfy(r -> assertThat(r.get("period_key")).isEqualTo(WEEK_KEY));

        assertThat((Double) rows.get(0).get("score")).isCloseTo(12.0, within(1e-9));
        assertThat((Double) rows.get(1).get("score")).isCloseTo(7.0, within(1e-9));
        assertThat((Double) rows.get(2).get("score")).isCloseTo(5.6, within(1e-9));
    }

    @DisplayName("구간 밖(전주) 메트릭은 주간 집계에 섞이지 않는다.")
    @Test
    void excludesMetricsOutsideThePeriod() throws Exception {
        jobLauncherTestUtils.launchJob(params(BASE_DATE, "WEEKLY", 2L));

        assertThat(weeklyRows()).extracting(r -> r.get("product_id")).doesNotContain(6L);
    }

    @DisplayName("여러 일자의 지표는 상품 단위로 합산된다.")
    @Test
    void sumsDailyRowsPerProduct() throws Exception {
        jobLauncherTestUtils.launchJob(params(BASE_DATE, "WEEKLY", 3L));

        Map<String, Object> product2 = weeklyRows().stream()
            .filter(r -> Long.valueOf(2L).equals(r.get("product_id")))
            .findFirst().orElseThrow();
        assertThat(product2.get("order_count")).isEqualTo(8L); // 5 + 3
    }

    @DisplayName("월간 Job 은 같은 기준일이라도 그 달 전체를 집계한다(전주 메트릭 포함).")
    @Test
    void aggregatesMonthly() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(params(BASE_DATE, "MONTHLY", 4L));

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM mv_product_rank_monthly WHERE period_key = ? ORDER BY rank_no", MONTH_KEY);
        // 6번(70.0) 이 1위, 그 다음 3번(12.0), 1번(7.0)
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("product_id")).containsExactly(6L, 3L, 1L);
    }

    @DisplayName("같은 기간을 다시 돌려도 중복 없이 같은 결과가 된다(재실행 멱등).")
    @Test
    void rerunIsIdempotent() throws Exception {
        jobLauncherTestUtils.launchJob(params(BASE_DATE, "WEEKLY", 5L));
        List<Map<String, Object>> first = weeklyRows();

        // 같은 주의 다른 날짜를 기준일로 줘도 같은 기간 키 → 기존 적재분을 지우고 다시 만든다
        JobExecution second = jobLauncherTestUtils.launchJob(params("20260726", "WEEKLY", 6L));

        assertThat(second.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rerun = weeklyRows();
        assertThat(rerun).hasSize(first.size());
        assertThat(rerun).extracting(r -> r.get("product_id"))
            .isEqualTo(first.stream().map(r -> r.get("product_id")).toList());
    }

    @DisplayName("청크 경계를 넘어서도 대상이 누락되지 않는다(GROUP BY 페이징).")
    @Test
    void readsEveryProductAcrossPages() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(params(BASE_DATE, "WEEKLY", 9L));

        StepExecution aggregateStep = execution.getStepExecutions().stream()
            .filter(s -> s.getStepName().equals("mvAggregateStep"))
            .findFirst().orElseThrow();

        // 주간 구간 안의 상품은 1~5 번 5개. chunk-size=2 이므로 3페이지에 걸쳐 읽힌다.
        assertThat(aggregateStep.getReadCount()).isEqualTo(5);
        assertThat(aggregateStep.getWriteCount()).isEqualTo(5);
        assertThat(aggregateStep.getCommitCount()).isGreaterThan(1);
    }

    @DisplayName("period 파라미터가 없으면 Job 은 실패한다.")
    @Test
    void failsWithoutPeriod() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("baseDate", BASE_DATE).addLong("run.id", 7L).toJobParameters());

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    @DisplayName("일간은 MV 대상이 아니므로 Job 은 실패한다.")
    @Test
    void failsForDailyPeriod() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(params(BASE_DATE, "DAILY", 8L));

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    private List<Map<String, Object>> weeklyRows() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM mv_product_rank_weekly WHERE period_key = ? ORDER BY rank_no", WEEK_KEY);
    }

    private static JobParameters params(String baseDate, String period, long runId) {
        return new JobParametersBuilder()
            .addString("baseDate", baseDate)
            .addString("period", period)
            .addLong("run.id", runId)
            .toJobParameters();
    }

    private void insert(long productId, String date, long likeDelta, long salesCount, long viewCount) {
        jdbcTemplate.update("""
            INSERT INTO product_metrics
              (product_id, metric_date, like_count, like_delta, sales_count, view_count, like_version, created_at, updated_at)
            VALUES (?, ?, 0, ?, ?, ?, 0, NOW(), NOW())
            """, productId, LocalDate.parse(date), likeDelta, salesCount, viewCount);
    }
}
