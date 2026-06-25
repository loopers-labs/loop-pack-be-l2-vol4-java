package com.loopers.product.infrastructure;

import com.loopers.product.application.ProductLikeSummaryChange;
import com.loopers.product.application.ProductLikeSummarySyncRepository;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalLong;

@RequiredArgsConstructor
@Component
public class ProductLikeSummarySyncRepositoryImpl implements ProductLikeSummarySyncRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public long lockCheckpoint(String checkpointName) {
        jdbcTemplate.update("""
                insert into product_like_summary_checkpoint(checkpoint_name, last_change_id, updated_at)
                values (?, 0, current_timestamp(6))
                on duplicate key update checkpoint_name = checkpoint_name
                """,
            checkpointName);

        Long lastChangeId = jdbcTemplate.queryForObject("""
                select last_change_id
                from product_like_summary_checkpoint
                where checkpoint_name = ?
                for update
                """,
            Long.class,
            checkpointName);
        return lastChangeId == null ? 0 : lastChangeId;
    }

    @Override
    public OptionalLong findNextChunkMaxChangeId(long lastChangeId, int chunkSize) {
        Long maxChangeId = jdbcTemplate.queryForObject("""
                select max(id)
                from (
                    select id
                    from product_like_count_change
                    where id > ?
                    order by id
                    limit ?
                ) chunk
                """,
            Long.class,
            lastChangeId,
            chunkSize);
        return maxChangeId == null ? OptionalLong.empty() : OptionalLong.of(maxChangeId);
    }

    @Override
    public List<ProductLikeSummaryChange> summarizeChanges(long lastChangeId, long maxChangeId) {
        return jdbcTemplate.query("""
                select product_id, sum(change_amount) as change_amount
                from product_like_count_change
                where id > ? and id <= ?
                group by product_id
                having sum(change_amount) <> 0
                order by product_id
                """,
            (rs, rowNum) -> new ProductLikeSummaryChange(
                rs.getLong("product_id"),
                rs.getLong("change_amount")
            ),
            lastChangeId,
            maxChangeId);
    }

    @Override
    public void applyChanges(List<ProductLikeSummaryChange> changes) {
        if (changes.isEmpty()) {
            return;
        }

        changes.forEach(change -> {
            int updatedCount = jdbcTemplate.update("""
                    update product_like_summary
                    set like_count = greatest(0, like_count + ?)
                    where product_id = ?
                    """,
                change.changeAmount(),
                change.productId());

            if (updatedCount != 1) {
                throw new CoreException(ErrorType.INTERNAL_ERROR, "상품 좋아요 요약 정보가 존재하지 않습니다.");
            }
        });
    }

    @Override
    public void advanceCheckpoint(String checkpointName, long lastChangeId) {
        jdbcTemplate.update("""
                update product_like_summary_checkpoint
                set last_change_id = ?, updated_at = current_timestamp(6)
                where checkpoint_name = ?
                """,
            lastChangeId,
            checkpointName);
    }
}
