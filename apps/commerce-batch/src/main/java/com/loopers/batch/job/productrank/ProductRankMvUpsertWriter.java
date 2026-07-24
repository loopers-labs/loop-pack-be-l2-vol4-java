package com.loopers.batch.job.productrank;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

public class ProductRankMvUpsertWriter implements ItemWriter<ProductRankScoreDelta> {

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate asOfDate;
    private final ProductRankMvTable table;

    public ProductRankMvUpsertWriter(JdbcTemplate jdbcTemplate, LocalDate asOfDate, ProductRankMvTable table) {
        this.jdbcTemplate = jdbcTemplate;
        this.asOfDate = asOfDate;
        this.table = table;
    }

    @Override
    public void write(Chunk<? extends ProductRankScoreDelta> chunk) {
        // VALUES() 함수는 MySQL 8.0.20부터 deprecated이므로, 신규 행을 별칭(new_row)으로 참조하는
        // row-alias 문법(8.0.19+)을 사용한다. 행 별칭의 컬럼명까지 원본과 다르게 지정해야
        // "score = new_row.score" 같은 참조에서 컬럼명 충돌(ambiguous column)이 나지 않는다.
        String sql = """
                INSERT INTO %s (as_of_date, product_id, score, view_sum, like_delta_sum, purchase_quantity_sum, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                AS new_row (new_as_of_date, new_product_id, new_score, new_view_sum, new_like_delta_sum, new_purchase_quantity_sum, new_created_at)
                ON DUPLICATE KEY UPDATE
                    score = score + new_row.new_score,
                    view_sum = view_sum + new_row.new_view_sum,
                    like_delta_sum = like_delta_sum + new_row.new_like_delta_sum,
                    purchase_quantity_sum = purchase_quantity_sum + new_row.new_purchase_quantity_sum
                """.formatted(table.tableName());

        List<Object[]> batchArgs = chunk.getItems().stream()
                .map(delta -> new Object[]{
                        asOfDate, delta.productId(), delta.scoreDelta(),
                        delta.viewDelta(), delta.likeDeltaDelta(), delta.purchaseDelta()
                })
                .toList();

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}
