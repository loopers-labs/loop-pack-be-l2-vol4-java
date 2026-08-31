package com.loopers.batch.job.ranking;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;

// Hides: stable source ordering and the restart position stored in ExecutionContext.
public class RankingItemReader implements ItemStreamReader<RankingItemReader.SourceRow> {
    public static final String INDEX_KEY = "reader.index";
    private final JdbcTemplate jdbcTemplate;
    private List<SourceRow> rows = List.of();
    private int index;

    public RankingItemReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SourceRow read() {
        return index < rows.size() ? rows.get(index++) : null;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        rows = jdbcTemplate.query(
            "select seq,event_id,product_id,score_delta,occurred_at from ranking_source order by seq",
            (rs, rowNum) -> new SourceRow(
                rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                rs.getTimestamp(5).toLocalDateTime()
            )
        );
        index = executionContext.getInt(INDEX_KEY, 0);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(INDEX_KEY, index);
    }

    @Override
    public void close() throws ItemStreamException {
        rows = List.of();
    }

    public record SourceRow(long seq, String eventId, long productId, long scoreDelta, LocalDateTime occurredAt) {}
}
