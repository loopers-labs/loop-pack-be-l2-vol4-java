package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step1 은 대상 기간만 비운다. 다른 기간·다른 주기의 행은 건드리지 않는다.
 * 별도 Step 이라 재시작 시 스킵되고, 그래야 Step2 가 이어서 채운 결과가 지워지지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class RankingDeleteTaskletTest {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    RankingDeleteTaskletTest(JdbcTemplate jdbcTemplate, DatabaseCleanUp databaseCleanUp) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        insert("mv_product_rank_weekly", "2026-W30", 100L);
        insert("mv_product_rank_weekly", "2026-W30", 200L);
        insert("mv_product_rank_weekly", "2026-W29", 100L);
        insert("mv_product_rank_monthly", "2026-07", 100L);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void insert(String table, String periodKey, long productId) {
        jdbcTemplate.update("""
                INSERT INTO %s (period_key, product_id, score, view_count, like_count, sales_count, created_at)
                VALUES (?, ?, 1.0, 1, 1, 1, NOW())
                """.formatted(table), periodKey, productId);
    }

    private int count(String table, String periodKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE period_key = ?".formatted(table), Integer.class, periodKey);
    }

    @Test
    @DisplayName("주간 삭제는 그 주 행만 지운다 — 다른 주는 남는다")
    void givenWeeklyPeriod_whenExecuted_thenOnlyThatWeekRemoved() throws Exception {
        RankingDeleteTasklet tasklet =
                new RankingDeleteTasklet(jdbcTemplate, RankingPeriod.WEEKLY, LocalDate.of(2026, 7, 26));

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(count("mv_product_rank_weekly", "2026-W30")).isZero();
        assertThat(count("mv_product_rank_weekly", "2026-W29")).isEqualTo(1);
    }

    @Test
    @DisplayName("주간 삭제는 월간 테이블을 건드리지 않는다")
    void givenWeeklyPeriod_whenExecuted_thenMonthlyUntouched() throws Exception {
        new RankingDeleteTasklet(jdbcTemplate, RankingPeriod.WEEKLY, LocalDate.of(2026, 7, 26))
                .execute(null, null);

        assertThat(count("mv_product_rank_monthly", "2026-07")).isEqualTo(1);
    }

    @Test
    @DisplayName("월간 삭제는 그 달 행만 지운다")
    void givenMonthlyPeriod_whenExecuted_thenOnlyThatMonthRemoved() throws Exception {
        new RankingDeleteTasklet(jdbcTemplate, RankingPeriod.MONTHLY, LocalDate.of(2026, 7, 22))
                .execute(null, null);

        assertThat(count("mv_product_rank_monthly", "2026-07")).isZero();
        assertThat(count("mv_product_rank_weekly", "2026-W30")).isEqualTo(2);
    }

    @Test
    @DisplayName("지울 것이 없어도 성공한다 — 첫 실행과 재실행이 같아야 한다")
    void givenNothingToDelete_whenExecuted_thenStillFinished() throws Exception {
        RankingDeleteTasklet tasklet =
                new RankingDeleteTasklet(jdbcTemplate, RankingPeriod.WEEKLY, LocalDate.of(2020, 1, 6));

        assertThat(tasklet.execute(null, null)).isEqualTo(RepeatStatus.FINISHED);
    }
}
