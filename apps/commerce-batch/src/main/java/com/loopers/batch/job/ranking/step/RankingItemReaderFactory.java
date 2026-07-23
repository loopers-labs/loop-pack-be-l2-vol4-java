package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.ranking.domain.RankingScoreWeights;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;

import javax.sql.DataSource;

/**
 * DB 가 집계·정렬·자르기를 한 번에 하고 Reader 는 그 결과 150 행을 받는다.
 *
 * <p>커서를 쓰는 이유는 {@code LIMIT}이다. JdbcPagingItemReader 는 완성된 SQL 을 받지 않고
 * 조각만 받아 조립하는데 LIMIT 자리를 pageSize 가 차지해, "상위 150"을 SQL 로 표현할 수 없다.
 * maxItemCount 라는 별개 노브로 나눠 표현해야 하고 빠뜨리면 컷이 사라져 전 상품이 흘러 들어온다.
 */
public final class RankingItemReaderFactory {

    private static final String SQL = """
            SELECT m.product_id,
                   SUM(m.view_count)  AS view_count,
                   SUM(m.like_count)  AS like_count,
                   SUM(m.sales_count) AS sales_count,
                   SUM(m.view_count) * ?
                     + SUM(m.like_count)  * ?
                     + SUM(m.sales_count) * ? AS score
            FROM product_metrics m
            JOIN products p ON p.id = m.product_id
            WHERE m.stat_date BETWEEN ? AND ?
              AND p.deleted_at IS NULL
              AND p.status = 'ON_SALE'
            GROUP BY m.product_id
            ORDER BY score DESC, product_id ASC
            LIMIT %d
            """.formatted(RankingPeriod.RANK_LIMIT);

    private RankingItemReaderFactory() {
    }

    public static JdbcCursorItemReader<RankingRow> cursor(DataSource dataSource,
                                                          RankingPeriod.Range range,
                                                          RankingScoreWeights weights) {
        return new JdbcCursorItemReaderBuilder<RankingRow>()
                .name("rankingCursorItemReader")
                .dataSource(dataSource)
                .sql(SQL)
                .preparedStatementSetter(ps -> {
                    ps.setDouble(1, weights.view());
                    ps.setDouble(2, weights.like());
                    ps.setDouble(3, weights.order());
                    ps.setObject(4, range.from());
                    ps.setObject(5, range.to());
                })
                .rowMapper((rs, rowNum) -> new RankingRow(
                        rs.getLong("product_id"),
                        rs.getLong("view_count"),
                        rs.getLong("like_count"),
                        rs.getLong("sales_count"),
                        rs.getDouble("score")))
                .build();
    }
}
