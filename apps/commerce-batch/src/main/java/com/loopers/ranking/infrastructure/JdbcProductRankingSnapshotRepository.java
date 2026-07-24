package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class JdbcProductRankingSnapshotRepository implements ProductRankingSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<ProductRankingSnapshotHeader> findBy(ProductRankingSnapshotKey key) {
        return jdbcTemplate.query(
            """
                select
                    id,
                    period,
                    period_start,
                    aggregation_end_date,
                    revision,
                    score_policy_version,
                    view_weight,
                    like_weight,
                    order_weight,
                    order_amount_unit,
                    created_at,
                    completed_at
                from product_rank_snapshots
                where period = ?
                  and aggregation_end_date = ?
                  and revision = ?
                """,
            this::mapSnapshot,
            key.period().name(),
            key.aggregationEndDate(),
            key.revision()
        ).stream().findFirst();
    }

    @Override
    public Optional<ProductRankingSnapshotHeader> findById(long snapshotId) {
        return jdbcTemplate.query(
            """
                select
                    id,
                    period,
                    period_start,
                    aggregation_end_date,
                    revision,
                    score_policy_version,
                    view_weight,
                    like_weight,
                    order_weight,
                    order_amount_unit,
                    created_at,
                    completed_at
                from product_rank_snapshots
                where id = ?
                """,
            this::mapSnapshot,
            snapshotId
        ).stream().findFirst();
    }

    @Override
    public boolean existsNewerCompletedThan(ProductRankingSnapshotKey key) {
        Integer exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from product_rank_snapshots
                    where period = ?
                      and completed_at is not null
                      and (
                          aggregation_end_date > ?
                          or (
                              aggregation_end_date = ?
                              and revision > ?
                          )
                      )
                )
                """,
            Integer.class,
            key.period().name(),
            key.aggregationEndDate(),
            key.aggregationEndDate(),
            key.revision()
        );
        return exists != null && exists == 1;
    }

    @Override
    public void insert(NewProductRankingSnapshot snapshot) {
        ProductRankingSnapshotKey key = snapshot.key();
        RankingScorePolicy policy = snapshot.scorePolicy();

        jdbcTemplate.update(
            """
                insert into product_rank_snapshots(
                    period,
                    period_start,
                    aggregation_end_date,
                    revision,
                    score_policy_version,
                    view_weight,
                    like_weight,
                    order_weight,
                    order_amount_unit,
                    created_at,
                    completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null)
                """,
            key.period().name(),
            key.periodStart(),
            key.aggregationEndDate(),
            key.revision(),
            policy.version(),
            policy.viewWeight(),
            policy.likeWeight(),
            policy.orderWeight(),
            policy.orderAmountUnit(),
            LocalDateTime.ofInstant(snapshot.createdAt(), ZoneOffset.UTC)
        );
    }

    @Override
    public boolean completeIfIncomplete(long snapshotId, Instant completedAt) {
        int updated = jdbcTemplate.update(
            """
                update product_rank_snapshots
                set completed_at = ?
                where id = ?
                  and completed_at is null
                """,
            LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC),
            snapshotId
        );
        return updated == 1;
    }

    private ProductRankingSnapshotHeader mapSnapshot(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {
        ProductRankingSnapshotKey key = new ProductRankingSnapshotKey(
            RankingPeriod.valueOf(resultSet.getString("period")),
            resultSet.getObject("aggregation_end_date", LocalDate.class),
            resultSet.getInt("revision")
        );
        LocalDate storedPeriodStart = resultSet.getObject("period_start", LocalDate.class);
        if (!key.periodStart().equals(storedPeriodStart)) {
            throw new IllegalStateException("stored product ranking period start is inconsistent");
        }

        RankingScorePolicy scorePolicy = RankingScorePolicy.from(
            resultSet.getString("score_policy_version"),
            resultSet.getDouble("view_weight"),
            resultSet.getDouble("like_weight"),
            resultSet.getDouble("order_weight"),
            resultSet.getLong("order_amount_unit")
        );

        return new ProductRankingSnapshotHeader(
            resultSet.getLong("id"),
            key,
            scorePolicy,
            toInstant(resultSet.getObject("created_at", LocalDateTime.class)),
            toInstant(resultSet.getObject("completed_at", LocalDateTime.class))
        );
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
