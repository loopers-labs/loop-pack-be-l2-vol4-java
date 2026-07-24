package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingAssignment;
import com.loopers.ranking.application.ProductRankingResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class JdbcProductRankingResultRepository
    implements ProductRankingResultRepository {

    private static final String WEEKLY_INSERT_SQL = """
        insert into mv_product_rank_weekly(
            snapshot_id,
            product_id,
            rank_no,
            score
        )
        values (?, ?, ?, ?)
        """;
    private static final String MONTHLY_INSERT_SQL = """
        insert into mv_product_rank_monthly(
            snapshot_id,
            product_id,
            rank_no,
            score
        )
        values (?, ?, ?, ?)
        """;
    private static final String WEEKLY_FIND_SQL = """
        select product_id, score, rank_no
        from mv_product_rank_weekly
        where snapshot_id = ?
        order by rank_no asc
        limit ?
        """;
    private static final String MONTHLY_FIND_SQL = """
        select product_id, score, rank_no
        from mv_product_rank_monthly
        where snapshot_id = ?
        order by rank_no asc
        limit ?
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insertAll(
        RankingPeriod period,
        long snapshotId,
        List<ProductRankingAssignment> rankings
    ) {
        String sql = insertSql(period);
        if (rankings.isEmpty()) {
            return;
        }

        List<Object[]> batchArguments = rankings.stream()
            .map(ranking -> new Object[]{
                snapshotId,
                ranking.productId(),
                ranking.rankNo(),
                ranking.score()
            })
            .toList();
        jdbcTemplate.batchUpdate(sql, batchArguments);
    }

    @Override
    public List<ProductRankingAssignment> findPublishedRankings(
        RankingPeriod period,
        long snapshotId,
        int limit
    ) {
        return jdbcTemplate.query(
            findSql(period),
            (resultSet, rowNumber) -> new ProductRankingAssignment(
                resultSet.getLong("product_id"),
                resultSet.getDouble("score"),
                resultSet.getInt("rank_no")
            ),
            snapshotId,
            limit
        );
    }

    private String insertSql(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> WEEKLY_INSERT_SQL;
            case MONTHLY -> MONTHLY_INSERT_SQL;
            case DAILY -> throw new IllegalArgumentException(
                "DAILY product ranking results are not supported"
            );
        };
    }

    private String findSql(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> WEEKLY_FIND_SQL;
            case MONTHLY -> MONTHLY_FIND_SQL;
            case DAILY -> throw new IllegalArgumentException(
                "DAILY product ranking results are not supported"
            );
        };
    }
}
