package com.loopers.batch.job.ranking;

import java.util.List;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

// Hides: event idempotency, ranking upsert, and deterministic rank recomputation.
public class RankingItemWriter implements ItemWriter<RankingItemReader.SourceRow> {
    public static final String SNAPSHOT_ID = "snapshot-2026w34";
    private final JdbcTemplate jdbcTemplate;

    public RankingItemWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends RankingItemReader.SourceRow> chunk) {
        for (var item : chunk) {
            int inserted = jdbcTemplate.update(
                "insert ignore into ranking_applied_event(snapshot_id, event_id) values (?, ?)",
                SNAPSHOT_ID, item.eventId()
            );
            if (inserted == 1) {
                jdbcTemplate.update(
                    "insert into weekly_ranking(snapshot_id, product_id, score, ranking_position) values (?, ?, ?, 0) " +
                        "on duplicate key update score = score + values(score)",
                    SNAPSHOT_ID, item.productId(), item.scoreDelta()
                );
            }
        }
        List<Long> productIds = jdbcTemplate.queryForList(
            "select product_id from weekly_ranking where snapshot_id = ? order by score desc, product_id asc",
            Long.class, SNAPSHOT_ID
        );
        for (int index = 0; index < productIds.size(); index++) {
            jdbcTemplate.update(
                "update weekly_ranking set ranking_position = ? where snapshot_id = ? and product_id = ?",
                index + 1, SNAPSHOT_ID, productIds.get(index)
            );
        }
    }
}
