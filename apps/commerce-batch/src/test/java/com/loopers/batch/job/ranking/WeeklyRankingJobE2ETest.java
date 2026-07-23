package com.loopers.batch.job.ranking;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * targetDate 없이 도는 것을 막는 게 이 Job 의 첫 계약이다.
 * 통과시키면 JobParameters 가 비어 완료 가드가 적용되지 않고, 두 번째 실행부터 Step 이 전부 스킵돼
 * 실패도 로그도 없이 아무것도 하지 않는다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        // 잡 빈을 등록하되(@ConditionalOnProperty) 부팅 시 자동 실행은 끈다.
        // 켜 두면 러너가 targetDate 없이 실행을 시도하다 검증에 걸려 컨텍스트가 통째로 죽는다
        // — 운영에서는 그게 맞는 동작이지만(빠른 실패), 여기서는 파라미터를 직접 주며 검증해야 한다.
        "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void insert(String periodKey, long productId) {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly
                    (period_key, product_id, score, view_count, like_count, sales_count, created_at)
                VALUES (?, ?, 1.0, 1, 1, 1, NOW())
                """, periodKey, productId);
    }

    private int count(String periodKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?", Integer.class, periodKey);
    }

    @Test
    @DisplayName("targetDate 없이 실행하면 시작조차 하지 못한다")
    void givenNoTargetDate_whenLaunched_thenRejectedBeforeStart() {
        jobLauncherTestUtils.setJob(job);

        assertThatThrownBy(jobLauncherTestUtils::launchJob)
                .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("targetDate 형식이 틀리면 시작하지 못한다")
    void givenMalformedTargetDate_whenLaunched_thenRejected() {
        jobLauncherTestUtils.setJob(job);

        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(
                new JobParametersBuilder().addString("targetDate", "2026-07-26").toJobParameters()))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("targetDate 가 속한 주의 기존 행을 비우고 완료한다")
    void givenTargetDate_whenLaunched_thenThatWeekCleared() throws Exception {
        insert("2026-W30", 100L);
        insert("2026-W29", 100L);
        jobLauncherTestUtils.setJob(job);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder().addString("targetDate", "20260726").toJobParameters());

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(count("2026-W30")).isZero();
        assertThat(count("2026-W29")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 targetDate 로 다시 돌리면 완료 가드가 막는다 — 재집계는 rerun 으로 명시한다")
    void givenSameTargetDateTwice_whenRelaunched_thenBlockedByCompletionGuard() throws Exception {
        jobLauncherTestUtils.setJob(job);
        var params = new JobParametersBuilder().addString("targetDate", "20260719").toJobParameters();
        jobLauncherTestUtils.launchJob(params);

        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(params))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    @DisplayName("rerun 을 바꾸면 같은 기간도 다시 집계할 수 있다")
    void givenDifferentRerun_whenRelaunched_thenAllowed() throws Exception {
        jobLauncherTestUtils.setJob(job);
        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("targetDate", "20260712").toJobParameters());

        JobExecution rerun = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("targetDate", "20260712").addLong("rerun", 1L).toJobParameters());

        assertThat(rerun.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }
}
