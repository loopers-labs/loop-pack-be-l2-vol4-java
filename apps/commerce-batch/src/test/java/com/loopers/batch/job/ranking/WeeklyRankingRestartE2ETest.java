package com.loopers.batch.job.ranking;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설계의 재시작 계약을 실제 실패로 검증한다 — "Step1(DELETE)은 완료되면 재시작 시 스킵되고,
 * Step2 는 읽던 위치부터 이어서 채워 최종 150행이 완성된다".
 *
 * <p>실패 주입: MV 테이블에 CHECK 제약을 걸어 두 번째 청크의 특정 상품 INSERT 를 실패시킨다.
 * 첫 청크(100행)는 커밋되고 두 번째 청크에서 Step2 가 FAILED 된다. 제약을 풀고 같은 파라미터로 재시작한다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class WeeklyRankingRestartE2ETest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 20);
    // score = view = product_id * 10 이라 순위는 product_id 내림차순. 1등 150번 … 150등 1번.
    // 두 번째 청크(101~150위)는 product_id 50~1. 그 안의 25번 INSERT 를 CHECK 로 막는다.
    private static final long BLOCKED_PRODUCT_ID = 25L;

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
        clearBatchMetadata();
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM product_metrics");
        for (long id = 1; id <= 150; id++) {
            jdbcTemplate.update("""
                    INSERT INTO products (id, brand_id, name, price, status, like_count, created_at, updated_at, deleted_at)
                    VALUES (?, 1, concat('p', ?), 1000, 'ON_SALE', 0, NOW(), NOW(), NULL)
                    """, id, id);
            jdbcTemplate.update("""
                    INSERT INTO product_metrics (stat_date, product_id, view_count, like_count, sales_count, updated_at)
                    VALUES (?, ?, ?, 0, 0, NOW())
                    """, MONDAY, id, id * 10);
        }
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        dropCheck();
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM product_metrics");
        databaseCleanUp.truncateAllTables();
        clearBatchMetadata();   // 같은 targetDate 를 쓰는 다른 테스트와 충돌하지 않게 남기지 않는다
    }

    /**
     * 배치 메타테이블을 비운다. 재시작 테스트는 "같은 파라미터로 두 번" 실행이 핵심이라,
     * 이전 실행(다른 테스트·이전 gradle run)이 남긴 JobInstance 가 있으면 첫 실행이 새 시작이 아니라
     * 재시작이 되어 Step 스킵이 어긋난다. 컨테이너를 공유하므로 매 테스트 시작 시 깨끗이 비운다.
     */
    private void clearBatchMetadata() {
        for (String table : List.of(
                "BATCH_STEP_EXECUTION_CONTEXT", "BATCH_STEP_EXECUTION",
                "BATCH_JOB_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_PARAMS",
                "BATCH_JOB_EXECUTION", "BATCH_JOB_INSTANCE")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    private void addCheck() {
        jdbcTemplate.execute(
                "ALTER TABLE mv_product_rank_weekly ADD CONSTRAINT chk_block CHECK (product_id <> " + BLOCKED_PRODUCT_ID + ")");
    }

    private void dropCheck() {
        try {
            jdbcTemplate.execute("ALTER TABLE mv_product_rank_weekly DROP CHECK chk_block");
        } catch (Exception ignored) {
            // 제약이 없으면 무시
        }
    }

    private JobExecution lastExecution;

    /**
     * void 로 둔다 — JobExecution 을 반환하면 @SpringBatchTest 의 JobScopeTestExecutionListener 가
     * 이 메서드를 테스트 준비 단계(@BeforeEach 보다 먼저)에서 자동 호출해, 메타 정리 전에 실행돼 버린다.
     * 결과는 필드로 넘긴다.
     */
    private void launch() throws Exception {
        JobParameters params = new JobParametersBuilder().addString("targetDate", "20260726").toJobParameters();
        lastExecution = jobLauncherTestUtils.launchJob(params);
    }

    private int mvCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = '2026-W30'", Integer.class);
    }

    private String stepStatus(JobExecution execution, String contains) {
        return execution.getStepExecutions().stream()
                .filter(s -> s.getStepName().contains(contains))
                .map(StepExecution::getStatus)
                .map(Enum::name)
                .findFirst().orElse("NONE");
    }

    @Test
    @DisplayName("두 번째 청크 실패 후 재시작하면 Step1 은 스킵되고 최종 150행이 완성된다")
    void givenSecondChunkFails_whenRestarted_thenStep1SkippedAndAll150Loaded() throws Exception {
        // 1차: 두 번째 청크에서 실패
        addCheck();
        launch();
        JobExecution first = lastExecution;

        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(stepStatus(first, "DeleteStep")).isEqualTo("COMPLETED");
        assertThat(stepStatus(first, "LoadStep")).isEqualTo("FAILED");
        assertThat(mvCount()).isEqualTo(100);   // 첫 청크만 커밋됨

        // 2차: 제약을 풀고 같은 파라미터로 재시작
        dropCheck();
        launch();
        JobExecution restart = lastExecution;

        assertThat(restart.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        // Step1 은 이미 COMPLETED 라 재실행되지 않는다
        assertThat(stepStatus(restart, "DeleteStep")).isEqualTo("NONE");
        assertThat(mvCount()).isEqualTo(150);

        // 중복·누락 없이 product_id 1..150 이 정확히 한 번씩
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT product_id FROM mv_product_rank_weekly WHERE period_key = '2026-W30' ORDER BY product_id",
                Long.class);
        assertThat(ids).hasSize(150);
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.get(0)).isEqualTo(1L);
        assertThat(ids.get(149)).isEqualTo(150L);
    }
}
