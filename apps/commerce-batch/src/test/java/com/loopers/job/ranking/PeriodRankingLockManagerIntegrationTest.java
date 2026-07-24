package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.PeriodRankingLockManager;
import com.loopers.ranking.RankingPeriod;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.batch.job.enabled=false",
    "spring.batch.job.name=none"
})
class PeriodRankingLockManagerIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("heartbeat renew는 설정된 lease만큼 owner lock 만료시각을 연장한다.")
    @Test
    void renewsOwnedLockLease() {
        LocalDate periodStart = LocalDate.of(2026, 7, 6);
        PeriodRankingLockManager lockManager = new PeriodRankingLockManager(
            jdbcTemplate,
            RankingPeriod.WEEKLY,
            120
        );
        lockManager.acquire(periodStart, 100L);
        jdbcTemplate.update("""
            UPDATE product_rank_job_lock
            SET expires_at = DATE_ADD(NOW(6), INTERVAL 1 SECOND)
            WHERE period_type = 'WEEKLY' AND period_start = ?
            """, periodStart);
        LocalDateTime shortenedExpiry = expiry(periodStart);

        lockManager.renew(periodStart, 100L);

        assertThat(expiry(periodStart)).isAfter(shortenedExpiry.plusSeconds(100));
    }

    @DisplayName("heartbeat 시 lock ownership을 잃었다면 즉시 실패한다.")
    @Test
    void rejectsRenewalAfterOwnershipLoss() {
        LocalDate periodStart = LocalDate.of(2026, 7, 6);
        PeriodRankingLockManager lockManager = new PeriodRankingLockManager(
            jdbcTemplate,
            RankingPeriod.WEEKLY,
            120
        );
        lockManager.acquire(periodStart, 100L);

        assertThatThrownBy(() -> lockManager.renew(periodStart, 200L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("lock");
    }

    private LocalDateTime expiry(LocalDate periodStart) {
        return jdbcTemplate.queryForObject("""
            SELECT expires_at
            FROM product_rank_job_lock
            WHERE period_type = 'WEEKLY' AND period_start = ?
            """, LocalDateTime.class, periodStart);
    }
}
