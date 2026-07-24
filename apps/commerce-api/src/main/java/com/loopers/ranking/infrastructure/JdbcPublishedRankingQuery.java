package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.PublishedRanking;
import com.loopers.ranking.application.PublishedRankingQuery;
import com.loopers.ranking.application.RankingPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class JdbcPublishedRankingQuery implements PublishedRankingQuery {

    private static final String LATEST_COMPLETED_SNAPSHOT_SQL = """
        select id
        from product_rank_snapshots
        where period = ?
          and period_start = ?
          and aggregation_end_date between ? and ?
          and completed_at is not null
        order by aggregation_end_date desc, revision desc
        limit 1
        """;
    private static final String WEEKLY_RANKING_SQL = """
        select rank_no, product_id
        from mv_product_rank_weekly
        where snapshot_id = ?
        order by rank_no asc
        """;
    private static final String MONTHLY_RANKING_SQL = """
        select rank_no, product_id
        from mv_product_rank_monthly
        where snapshot_id = ?
        order by rank_no asc
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<PublishedRanking> findLatestCompleted(
        RankingPeriod period,
        LocalDate date
    ) {
        String rankingSql = rankingSql(period);
        LocalDate periodStart = period.periodStart(date);
        LocalDate periodEnd = period.periodEnd(date);
        List<Long> snapshotIds = jdbcTemplate.query(
            LATEST_COMPLETED_SNAPSHOT_SQL,
            (resultSet, rowNumber) -> resultSet.getLong("id"),
            period.name(),
            periodStart,
            periodStart,
            periodEnd
        );
        if (snapshotIds.isEmpty()) {
            return Optional.empty();
        }

        List<RankingPosition> positions = jdbcTemplate.query(
            rankingSql,
            (resultSet, rowNumber) -> new RankingPosition(
                resultSet.getLong("rank_no"),
                resultSet.getLong("product_id")
            ),
            snapshotIds.getFirst()
        );
        return Optional.of(new PublishedRanking(positions));
    }

    private String rankingSql(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> WEEKLY_RANKING_SQL;
            case MONTHLY -> MONTHLY_RANKING_SQL;
            case DAILY -> throw new IllegalArgumentException(
                "DAILY published ranking is not supported"
            );
        };
    }
}
