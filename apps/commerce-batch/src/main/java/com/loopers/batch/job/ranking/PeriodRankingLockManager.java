package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

public final class PeriodRankingLockManager {
    private final JdbcTemplate jdbcTemplate;
    private final RankingPeriod period;
    private final long leaseSeconds;

    public PeriodRankingLockManager(JdbcTemplate jdbcTemplate, RankingPeriod period, long leaseSeconds) {
        if (leaseSeconds <= 0) {
            throw new IllegalArgumentException("랭킹 lock lease는 1초 이상이어야 합니다.");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.period = period;
        this.leaseSeconds = leaseSeconds;
    }

    public void acquire(LocalDate periodStart, long ownerId) {
        jdbcTemplate.update("""
            DELETE FROM product_rank_job_lock
            WHERE period_type = ? AND period_start = ? AND expires_at < NOW(6)
            """, period.name(), periodStart);
        try {
            jdbcTemplate.update("""
                INSERT INTO product_rank_job_lock
                    (period_type, period_start, owner_id, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, TIMESTAMPADD(SECOND, ?, NOW(6)), NOW(6), NOW(6))
                """, period.name(), periodStart, ownerId, leaseSeconds);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("같은 기간의 랭킹 Job이 이미 실행 중입니다.", exception);
        }
    }

    public void renew(LocalDate periodStart, long ownerId) {
        int updated = jdbcTemplate.update("""
            UPDATE product_rank_job_lock
            SET expires_at = TIMESTAMPADD(SECOND, ?, NOW(6)),
                updated_at = NOW(6)
            WHERE period_type = ?
              AND period_start = ?
              AND owner_id = ?
              AND expires_at >= NOW(6)
            """, leaseSeconds, period.name(), periodStart, ownerId);
        if (updated != 1) {
            throw new IllegalStateException("랭킹 lock 소유권이 만료되었거나 다른 실행으로 이전되었습니다.");
        }
    }

    public void release(LocalDate periodStart, long ownerId) {
        jdbcTemplate.update("""
            DELETE FROM product_rank_job_lock
            WHERE period_type = ? AND period_start = ? AND owner_id = ?
            """, period.name(), periodStart, ownerId);
    }
}
