package com.loopers.ranking.domain;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MV 는 조회가 압도적인 지면을 받치므로 스키마 자체가 계약이다.
 * PK 와 (period_key, score DESC, product_id ASC) 인덱스가 실제로 생성되는지 확인한다 —
 * MySQL 8.0 은 내림차순 인덱스를 진짜로 지원하므로 DESC 가 무시되면 안 된다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class ProductRankMvSchemaTest {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductRankMvSchemaTest(JdbcTemplate jdbcTemplate, DatabaseCleanUp databaseCleanUp) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private List<Map<String, Object>> indexColumns(String table, String index) {
        return jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME, SEQ_IN_INDEX, COLLATION
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                ORDER BY SEQ_IN_INDEX
                """, table, index);
    }

    @Test
    @DisplayName("주간 MV 의 PK 는 기간·상품 두 컬럼이다 — 같은 기간의 같은 상품은 한 행뿐")
    void givenWeeklyMv_whenInspected_thenPrimaryKeyIsPeriodAndProduct() {
        List<Map<String, Object>> pk = indexColumns("mv_product_rank_weekly", "PRIMARY");

        assertThat(pk).hasSize(2);
        assertThat(pk).extracting(c -> c.get("COLUMN_NAME"))
                .containsExactlyInAnyOrder("period_key", "product_id");
    }

    @Test
    @DisplayName("주간 MV 조회 인덱스는 score 가 내림차순으로 잡힌다")
    void givenWeeklyMv_whenInspected_thenScoreIndexIsDescending() {
        List<Map<String, Object>> idx = indexColumns("mv_product_rank_weekly", "idx_period_score");

        assertThat(idx).hasSize(3);
        assertThat(idx.get(0)).containsEntry("COLUMN_NAME", "period_key").containsEntry("COLLATION", "A");
        assertThat(idx.get(1)).containsEntry("COLUMN_NAME", "score").containsEntry("COLLATION", "D");
        assertThat(idx.get(2)).containsEntry("COLUMN_NAME", "product_id").containsEntry("COLLATION", "A");
    }

    @Test
    @DisplayName("월간 MV 도 같은 형태로 만들어진다")
    void givenMonthlyMv_whenInspected_thenSameShapeAsWeekly() {
        assertThat(indexColumns("mv_product_rank_monthly", "PRIMARY")).hasSize(2);

        List<Map<String, Object>> idx = indexColumns("mv_product_rank_monthly", "idx_period_score");
        assertThat(idx).hasSize(3);
        assertThat(idx.get(1)).containsEntry("COLUMN_NAME", "score").containsEntry("COLLATION", "D");
    }

    @Test
    @DisplayName("같은 기간·상품을 다시 INSERT 하면 PK 가 막는다 — 재적재는 DELETE 후에만 가능하다")
    void givenSamePeriodAndProduct_whenInsertedTwice_thenRejectedByPrimaryKey() {
        String insert = """
                INSERT INTO mv_product_rank_weekly
                    (period_key, product_id, score, view_count, like_count, sales_count, created_at)
                VALUES ('2026-W30', 100, 12.5, 10, 5, 1, NOW())
                """;
        jdbcTemplate.update(insert);

        assertThatThrownBy(() -> jdbcTemplate.update(insert))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = '2026-W30'", Integer.class))
                .isEqualTo(1);
    }
}
