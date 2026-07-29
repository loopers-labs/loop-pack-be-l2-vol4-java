package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingMvRepository;
import com.loopers.ranking.domain.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MV 를 JDBC 로 읽는다. 테이블은 batch 소유라 commerce-api 엔 엔티티가 없다.
 * 테이블명은 {@link RankingPeriod} 상수에서 오므로 SQL 에 외부 입력이 닿지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class RankingMvRepositoryImpl implements RankingMvRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RankingEntry> findPage(RankingPeriod period, String periodKey, long offset, int limit) {
        String sql = """
                SELECT product_id, score FROM %s
                WHERE period_key = ?
                ORDER BY score DESC, product_id ASC
                LIMIT ? OFFSET ?
                """.formatted(period.tableName());

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new RankingEntry(rs.getLong("product_id"), rs.getDouble("score")),
                periodKey, limit, offset);
    }

    @Override
    public long count(RankingPeriod period, String periodKey) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE period_key = ?".formatted(period.tableName()),
                Long.class, periodKey);
        return count == null ? 0L : count;
    }
}
