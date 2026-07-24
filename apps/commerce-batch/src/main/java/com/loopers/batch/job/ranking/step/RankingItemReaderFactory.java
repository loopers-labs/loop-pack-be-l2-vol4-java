package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.ranking.domain.RankingScoreWeights;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DB 가 집계·정렬·자르기를 한 번에 하고 Reader 는 그 결과 150 행을 받는다.
 * 커서를 기본으로 쓰되 페이징도 남겨 설정으로 전환·비교할 수 있게 한다.
 *
 * <p>커서를 기본으로 두는 이유는 {@code LIMIT}이다. 페이징 리더는 완성된 SQL 을 받지 않고
 * 조각만 받아 조립하는데 LIMIT 자리를 pageSize 가 차지해, "상위 150"을 SQL 로 표현할 수 없다.
 * maxItemCount 라는 별개 노브로 나눠 표현해야 하고 빠뜨리면 컷이 사라져 전 상품이 흘러 들어온다.
 */
public final class RankingItemReaderFactory {

    /** 커서용 완성 SQL. LIMIT 을 SQL 에 직접 쓴다. */
    private static final String CURSOR_SQL = """
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

    /** 페이징은 조각으로 준다. 가중치·기간은 named 파라미터로 — sort 조건이 붙는 자리와 섞이지 않게. */
    private static final String SELECT_CLAUSE = "m.product_id, "
            + "SUM(m.view_count) AS view_count, SUM(m.like_count) AS like_count, SUM(m.sales_count) AS sales_count, "
            + "SUM(m.view_count) * :viewWeight + SUM(m.like_count) * :likeWeight + SUM(m.sales_count) * :orderWeight AS score";
    private static final String FROM_CLAUSE = "product_metrics m JOIN products p ON p.id = m.product_id";
    private static final String WHERE_CLAUSE =
            "m.stat_date BETWEEN :from AND :to AND p.deleted_at IS NULL AND p.status = 'ON_SALE'";
    private static final String GROUP_CLAUSE = "m.product_id";

    /** 청크와 같은 100. 150 으로 두면 1페이지에 끝나 커서와 구분이 안 되고, 2페이지 재집계 비용도 드러나지 않는다. */
    private static final int PAGE_SIZE = 100;

    private static final RowMapper<RankingRow> ROW_MAPPER = (rs, rowNum) -> new RankingRow(
            rs.getLong("product_id"),
            rs.getLong("view_count"),
            rs.getLong("like_count"),
            rs.getLong("sales_count"),
            rs.getDouble("score"));

    /** 어느 리더를 쓸지. 설정(ranking.reader.type)으로 고른다. 기본은 커서. */
    public enum Type {CURSOR, PAGING}

    private RankingItemReaderFactory() {
    }

    /** 설정이 고른 리더를 만든다. 둘 다 같은 150개를 같은 순서로 낸다(RankingItemReaderFactoryTest 로 고정). */
    public static ItemStreamReader<RankingRow> create(Type type, DataSource dataSource,
                                                      RankingPeriod.Range range, RankingScoreWeights weights) {
        return type == Type.PAGING ? paging(dataSource, range, weights) : cursor(dataSource, range, weights);
    }

    public static JdbcCursorItemReader<RankingRow> cursor(DataSource dataSource,
                                                          RankingPeriod.Range range,
                                                          RankingScoreWeights weights) {
        return new JdbcCursorItemReaderBuilder<RankingRow>()
                .name("rankingCursorItemReader")
                .dataSource(dataSource)
                .sql(CURSOR_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setDouble(1, weights.view());
                    ps.setDouble(2, weights.like());
                    ps.setDouble(3, weights.order());
                    ps.setObject(4, range.from());
                    ps.setObject(5, range.to());
                })
                .rowMapper(ROW_MAPPER)
                .build();
    }

    /**
     * 페이징 리더. pageSize(=100)가 페이지별 LIMIT 이고, "상위 150" 컷은 maxItemCount 로 준다.
     * 둘이 맞아야 커서와 같은 결과가 나오고, maxItemCount 를 빠뜨리면 전 상품이 흘러 들어온다.
     */
    public static JdbcPagingItemReader<RankingRow> paging(DataSource dataSource,
                                                          RankingPeriod.Range range,
                                                          RankingScoreWeights weights) {
        MySqlPagingQueryProvider provider = new MySqlPagingQueryProvider();
        provider.setSelectClause(SELECT_CLAUSE);
        provider.setFromClause(FROM_CLAUSE);
        provider.setWhereClause(WHERE_CLAUSE);
        provider.setGroupClause(GROUP_CLAUSE);

        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("score", Order.DESCENDING);
        sortKeys.put("product_id", Order.ASCENDING);
        provider.setSortKeys(sortKeys);

        return new JdbcPagingItemReaderBuilder<RankingRow>()
                .name("rankingPagingItemReader")
                .dataSource(dataSource)
                .queryProvider(provider)
                .parameterValues(Map.of(
                        "viewWeight", weights.view(),
                        "likeWeight", weights.like(),
                        "orderWeight", weights.order(),
                        "from", range.from(),
                        "to", range.to()))
                .pageSize(PAGE_SIZE)
                .maxItemCount(RankingPeriod.RANK_LIMIT)
                .rowMapper(ROW_MAPPER)
                .build();
    }
}
