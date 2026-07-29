package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.domain.RankingPeriod;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;

import javax.sql.DataSource;

/**
 * MV 에 그대로 적재한다. 대상 테이블은 RankingPeriod 상수에서 오므로 외부 입력이 닿지 않는다
 * — JDBC 플레이스홀더는 값만 바인딩하고 식별자는 못 바인딩한다.
 */
public final class RankingItemWriterFactory {

    private RankingItemWriterFactory() {
    }

    public static JdbcBatchItemWriter<RankingMvRow> forPeriod(DataSource dataSource, RankingPeriod period) {
        String sql = """
                INSERT INTO %s (period_key, product_id, score, view_count, like_count, sales_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """.formatted(period.tableName());

        return new JdbcBatchItemWriterBuilder<RankingMvRow>()
                .dataSource(dataSource)
                .sql(sql)
                .itemPreparedStatementSetter((row, ps) -> {
                    ps.setString(1, row.periodKey());
                    ps.setLong(2, row.productId());
                    ps.setDouble(3, row.score());
                    ps.setLong(4, row.viewCount());
                    ps.setLong(5, row.likeCount());
                    ps.setLong(6, row.salesCount());
                })
                .build();
    }
}
