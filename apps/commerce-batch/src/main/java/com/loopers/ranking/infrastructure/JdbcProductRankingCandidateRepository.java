package com.loopers.ranking.infrastructure;

import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.RankingCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class JdbcProductRankingCandidateRepository
    implements ProductRankingCandidateRepository {

    private static final String UPSERT_SQL = """
        insert into product_rank_candidates(
            snapshot_id,
            product_id,
            score
        )
        values (?, ?, ?)
        on duplicate key update
            score = ?
        """;
    private static final String COUNT_SQL = """
        select count(*)
        from product_rank_candidates
        where snapshot_id = ?
        """;
    private static final String FIND_TOP_SQL = """
        select product_id, score
        from product_rank_candidates
        where snapshot_id = ?
        order by score desc, product_id asc
        limit ?
        """;
    private static final String DELETE_SQL = """
        delete from product_rank_candidates
        where snapshot_id = ?
        order by product_id
        limit ?
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void upsertAll(List<? extends RankingCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }

        List<Object[]> batchArguments = candidates.stream()
            .map(candidate -> new Object[]{
                candidate.snapshotId(),
                candidate.productId(),
                candidate.score(),
                candidate.score()
            })
            .toList();
        jdbcTemplate.batchUpdate(UPSERT_SQL, batchArguments);
    }

    @Override
    public long countCandidates(long snapshotId) {
        Long count = jdbcTemplate.queryForObject(
            COUNT_SQL,
            Long.class,
            snapshotId
        );
        if (count == null) {
            throw new IllegalStateException("product ranking candidate count is missing");
        }
        return count;
    }

    @Override
    public List<RankingCandidate> findTopCandidates(
        long snapshotId,
        int limit
    ) {
        return jdbcTemplate.query(
            FIND_TOP_SQL,
            (resultSet, rowNumber) -> new RankingCandidate(
                snapshotId,
                resultSet.getLong("product_id"),
                resultSet.getDouble("score")
            ),
            snapshotId,
            limit
        );
    }

    @Override
    public int deleteCandidates(long snapshotId, int limit) {
        return jdbcTemplate.update(DELETE_SQL, snapshotId, limit);
    }
}
