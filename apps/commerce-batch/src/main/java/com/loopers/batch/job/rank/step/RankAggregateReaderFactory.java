package com.loopers.batch.job.rank.step;

import com.loopers.batch.job.rank.MetricsAggregate;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;

import javax.sql.DataSource;
import java.time.LocalDate;

/**
 * 기간 내 product_metrics 를 상품별로 합산(GROUP BY)해 커서로 스트리밍 읽는 Chunk Reader 를 만든다. 대량 데이터를 한 번에 로딩하지 않고 훑는다.
 */
public final class RankAggregateReaderFactory {

    private static final String AGGREGATE_SQL = """
            SELECT product_id,
                   SUM(view_count)  AS view_sum,
                   SUM(like_count)  AS like_sum,
                   SUM(sales_count) AS sales_sum
            FROM product_metrics
            WHERE metric_date BETWEEN ? AND ?
            GROUP BY product_id
            """;

    private RankAggregateReaderFactory() {
    }

    public static JdbcCursorItemReader<MetricsAggregate> create(DataSource dataSource, LocalDate start, LocalDate end) {
        return new JdbcCursorItemReaderBuilder<MetricsAggregate>()
                .name("rankAggregateReader")
                .dataSource(dataSource)
                .sql(AGGREGATE_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, start);
                    ps.setObject(2, end);
                })
                .rowMapper((rs, rowNum) -> new MetricsAggregate(
                        rs.getLong("product_id"),
                        rs.getLong("view_sum"),
                        rs.getLong("like_sum"),
                        rs.getLong("sales_sum")
                ))
                .build();
    }
}