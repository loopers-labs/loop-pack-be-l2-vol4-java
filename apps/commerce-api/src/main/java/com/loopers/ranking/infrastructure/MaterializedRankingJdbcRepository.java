package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.MaterializedRankingRepository;
import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingPage;
import com.loopers.ranking.domain.RankingPeriod;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Repository
public class MaterializedRankingJdbcRepository implements MaterializedRankingRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RankingPage findPage(
            RankingPeriod period, LocalDate aggregationDate, int page, int size) {
        String tableName = tableName(period);
        Long totalCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + tableName
                                + " WHERE aggregation_date = ?",
                        Long.class,
                        aggregationDate);

        if (totalCount == null || totalCount == 0L) {
            return RankingPage.empty();
        }

        long offset = Math.multiplyExact((long) page - 1L, size);
        List<RankingEntry> entries =
                jdbcTemplate.query(
                        "SELECT product_id, rank_position, score FROM "
                                + tableName
                                + " WHERE aggregation_date = ?"
                                + " ORDER BY rank_position ASC LIMIT ? OFFSET ?",
                        (resultSet, rowNumber) ->
                                new RankingEntry(
                                        resultSet.getLong("product_id"),
                                        resultSet.getLong("rank_position"),
                                        resultSet.getBigDecimal("score").doubleValue()),
                        aggregationDate,
                        size,
                        offset);

        return new RankingPage(entries, totalCount);
    }

    private String tableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY ->
                    throw new IllegalArgumentException(
                            "일간 랭킹은 Redis 저장소에서 조회해야 합니다.");
        };
    }
}
