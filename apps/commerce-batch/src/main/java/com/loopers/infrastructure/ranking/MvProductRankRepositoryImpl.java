package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * MV 어댑터 — 대상 테이블은 기간에 따라 갈린다. 테이블명은 enum 에서만 결정되므로(외부 입력 아님)
 * SQL 문자열 조립이 주입 위험을 만들지 않는다.
 */
@RequiredArgsConstructor
@Component
public class MvProductRankRepositoryImpl implements MvProductRankRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int deletePeriod(RankingPeriod period, String periodKey) {
        return jdbcTemplate.update("DELETE FROM " + tableOf(period) + " WHERE period_key = ?", periodKey);
    }

    @Override
    public List<Long> findTopProductIds(RankingPeriod period, String periodKey, int limit) {
        return jdbcTemplate.queryForList(
            "SELECT product_id FROM " + tableOf(period)
                + " WHERE period_key = ? ORDER BY score DESC, product_id ASC LIMIT ?",
            Long.class, periodKey, limit);
    }

    @Override
    public void assignRanks(RankingPeriod period, String periodKey, List<Long> orderedProductIds) {
        if (orderedProductIds.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
            "UPDATE " + tableOf(period) + " SET rank_no = ?, updated_at = NOW() WHERE period_key = ? AND product_id = ?",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setInt(1, i + 1); // 1-based
                    ps.setString(2, periodKey);
                    ps.setLong(3, orderedProductIds.get(i));
                }

                @Override
                public int getBatchSize() {
                    return orderedProductIds.size();
                }
            });
    }

    @Override
    public int deleteUnranked(RankingPeriod period, String periodKey) {
        return jdbcTemplate.update(
            "DELETE FROM " + tableOf(period) + " WHERE period_key = ? AND rank_no IS NULL", periodKey);
    }

    private static String tableOf(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException(
                "일간 랭킹은 MV 로 적재하지 않습니다(Redis 실시간 랭킹이 담당).");
        };
    }
}
