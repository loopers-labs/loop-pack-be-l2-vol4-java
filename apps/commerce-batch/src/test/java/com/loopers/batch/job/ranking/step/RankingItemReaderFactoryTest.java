package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.ranking.domain.RankingScoreWeights;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 커서와 페이징이 같은 150개를 같은 순서로 내는지 확인한다.
 * 페이징 pageSize 는 청크와 같은 100 이라 2페이지가 생기고, 그 경계에서 순서가 어긋나면 안 된다.
 * products·product_metrics 는 배치 classpath 에 엔티티가 없어 JDBC 로만 읽으므로 테이블만 만든다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class RankingItemReaderFactoryTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 20);
    private static final RankingScoreWeights WEIGHTS = new RankingScoreWeights(0.1, 0.2, 0.6);
    private static final RankingPeriod.Range RANGE = RankingPeriod.WEEKLY.resolve(LocalDate.of(2026, 7, 26));

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    RankingItemReaderFactoryTest(DataSource dataSource, JdbcTemplate jdbcTemplate, DatabaseCleanUp databaseCleanUp) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id bigint NOT NULL AUTO_INCREMENT, brand_id bigint NOT NULL, name varchar(255) NOT NULL,
                    price bigint NOT NULL, status varchar(32) NOT NULL, like_count bigint NOT NULL DEFAULT 0,
                    created_at datetime(6) NOT NULL, updated_at datetime(6) NOT NULL, deleted_at datetime(6) NULL,
                    PRIMARY KEY (id))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics (
                    stat_date date NOT NULL, product_id bigint NOT NULL, view_count bigint NOT NULL,
                    like_count bigint NOT NULL, sales_count bigint NOT NULL, updated_at datetime(6) NULL,
                    PRIMARY KEY (stat_date, product_id))
                """);
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM product_metrics");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM product_metrics");
        databaseCleanUp.truncateAllTables();
    }

    private void seed(int products) {
        for (long id = 1; id <= products; id++) {
            jdbcTemplate.update("""
                    INSERT INTO products (id, brand_id, name, price, status, like_count, created_at, updated_at, deleted_at)
                    VALUES (?, 1, concat('p', ?), 1000, 'ON_SALE', 0, NOW(), NOW(), NULL)
                    """, id, id);
            // score = view * 0.1, 상품마다 다르게 줘서 순서가 유일하게 결정되도록
            jdbcTemplate.update("""
                    INSERT INTO product_metrics (stat_date, product_id, view_count, like_count, sales_count, updated_at)
                    VALUES (?, ?, ?, 0, 0, NOW())
                    """, MONDAY, id, id * 10);
        }
    }

    private List<RankingRow> drain(ItemStreamReader<RankingRow> reader) throws Exception {
        // 프로덕션에선 @Bean 이라 컨테이너가 afterPropertiesSet 을 부른다. 직접 생성하는 여기선 수동으로.
        if (reader instanceof org.springframework.beans.factory.InitializingBean bean) {
            bean.afterPropertiesSet();
        }
        List<RankingRow> rows = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            RankingRow row;
            while ((row = reader.read()) != null) {
                rows.add(row);
            }
        } finally {
            reader.close();
        }
        return rows;
    }

    @Test
    @DisplayName("커서와 페이징은 같은 150개를 같은 순서로 낸다")
    void givenSameData_whenReadByCursorAndPaging_thenIdenticalResult() throws Exception {
        seed(200);

        List<RankingRow> cursor = drain(RankingItemReaderFactory.cursor(dataSource, RANGE, WEIGHTS));
        List<RankingRow> paging = drain(RankingItemReaderFactory.paging(dataSource, RANGE, WEIGHTS));

        assertThat(cursor).hasSize(150);
        assertThat(paging).hasSize(150);
        assertThat(paging).containsExactlyElementsOf(cursor);
    }

    @Test
    @DisplayName("페이징도 상위 150 컷을 지킨다 — 2페이지 경계를 넘겨도 151번째부터는 오지 않는다")
    void givenPaging_whenDrained_thenStopsAt150() throws Exception {
        seed(200);

        List<RankingRow> paging = drain(RankingItemReaderFactory.paging(dataSource, RANGE, WEIGHTS));

        assertThat(paging).hasSize(150);
        // score 내림차순이므로 1등은 product 200 (view 2000), 150등은 product 51
        assertThat(paging.getFirst().productId()).isEqualTo(200L);
        assertThat(paging.getLast().productId()).isEqualTo(51L);
    }

    @Test
    @DisplayName("데이터가 150개 미만이면 있는 만큼만 낸다")
    void givenFewerThanLimit_whenRead_thenAllReturned() throws Exception {
        seed(30);

        assertThat(drain(RankingItemReaderFactory.cursor(dataSource, RANGE, WEIGHTS))).hasSize(30);
        assertThat(drain(RankingItemReaderFactory.paging(dataSource, RANGE, WEIGHTS))).hasSize(30);
    }

    @Test
    @DisplayName("페이징 2페이지 SQL 은 서브쿼리로 감싸며 그 안에 LIMIT 이 없다 — 매 페이지 기간 전체를 다시 집계한다")
    void givenGroupedPaging_whenGeneratingRemainingPages_thenReaggregatesWithoutInnerLimit() throws Exception {
        var provider = new org.springframework.batch.item.database.support.MySqlPagingQueryProvider();
        provider.setSelectClause("m.product_id, SUM(m.view_count) * :viewWeight AS score");
        provider.setFromClause("product_metrics m JOIN products p ON p.id = m.product_id");
        provider.setWhereClause("m.stat_date BETWEEN :from AND :to");
        provider.setGroupClause("m.product_id");
        var sortKeys = new java.util.LinkedHashMap<String, org.springframework.batch.item.database.Order>();
        sortKeys.put("score", org.springframework.batch.item.database.Order.DESCENDING);
        sortKeys.put("product_id", org.springframework.batch.item.database.Order.ASCENDING);
        provider.setSortKeys(sortKeys);
        provider.init(dataSource);

        String firstPage = provider.generateFirstPageQuery(100);
        String remaining = provider.generateRemainingPagesQuery(100);

        assertThat(firstPage).endsWith("LIMIT 100");
        assertThat(remaining)
                .as("2페이지부터는 GROUP BY 결과를 서브쿼리로 감싸고 바깥에서 keyset 조건 + LIMIT 을 건다")
                .contains("AS MAIN_QRY")
                .contains("GROUP BY m.product_id) AS MAIN_QRY")
                .endsWith("LIMIT 100");
    }
}
